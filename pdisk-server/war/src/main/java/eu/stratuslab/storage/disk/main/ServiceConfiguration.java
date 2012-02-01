package eu.stratuslab.storage.disk.main;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.utils.FileUtils;
import eu.stratuslab.storage.disk.utils.ProcessUtils;

public class ServiceConfiguration {

	private static final ServiceConfiguration instance = new ServiceConfiguration();

	public enum DiskType {
		LVM, FILE;

		public static DiskType valueOfIgnoreCase(String value) {
			try {
				return valueOf(value.toUpperCase());
			} catch (IllegalArgumentException e) {
				throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
						"illegal value for disk.store.iscsi.type: " + value);
			}
		}
	}

	public enum ShareType {
		ISCSI, NFS;

		public static ShareType valueOfIgnoreCase(String value) {
			try {
				return valueOf(value.toUpperCase());
			} catch (IllegalArgumentException e) {
				throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
						"illegal value for disk.store.share: " + value);
			}
		}

	}

	// Configuration file
	public static final String DEFAULT_CFG_FILENAME = "/etc/stratuslab/pdisk.cfg";
	public static final String DEFAULT_ISCSI_CONFIG_FILENAME = "/etc/stratuslab/iscsi.conf";
	public static final String ISCSI_CONFIG_FILENAME_SYS_PARAM_NAME = "iscsi.config.filename";

	// Disk size limits (in GiBs)
	public static final int DISK_SIZE_MIN = 1;
	public static final int DISK_SIZE_MAX = 1024;

	public final Properties CONFIGURATION;

	public final ShareType SHARE_TYPE;

	public final String ZK_ADDRESSES;

	public final DiskType ISCSI_DISK_TYPE;
	public final File ISCSI_CONFIG;
	public final String ISCSI_ADMIN;

	public final String LVM_GROUP_PATH;
	public final String VGDISPLAY_CMD;
	public final String LVCREATE_CMD;
	public final String LVREMOVE_CMD;
    public final String LVCHANGE_CMD;
    public final String DMSETUP_CMD;


	public final File STORAGE_LOCATION;

	public final int USERS_PER_DISK;

	public final String CLOUD_NODE_SSH_KEY;
	public final String CLOUD_NODE_ADMIN;
	public final String CLOUD_NODE_VM_DIR;
	public final String CLOUD_SERVICE_USER;
	
	public final String CACHE_LOCATION;

	private ServiceConfiguration() {

		CONFIGURATION = readConfigFile();

		SHARE_TYPE = getShareType();

		ZK_ADDRESSES = getConfigValue("disk.store.zookeeper.address");

		VGDISPLAY_CMD = getCommand("disk.store.lvm.vgdisplay");
		LVCREATE_CMD = getCommand("disk.store.lvm.create");
		LVREMOVE_CMD = getCommand("disk.store.lvm.remove");
		LVM_GROUP_PATH = getLVMGroup();
        LVCHANGE_CMD = getCommand("disk.store.lvm.lvchange");
        DMSETUP_CMD = getCommand("disk.store.lvm.dmsetup");


		ISCSI_DISK_TYPE = getDiskType();
		ISCSI_CONFIG = getISCSIConfig();
		ISCSI_ADMIN = getCommand("disk.store.iscsi.admin");

		STORAGE_LOCATION = getDiskLocation();

		USERS_PER_DISK = getUsersPerDisks();

		CLOUD_NODE_SSH_KEY = getConfigValue("disk.store.cloud.node.ssh_keyfile");
		CLOUD_NODE_ADMIN = getConfigValue("disk.store.cloud.node.admin");
		CLOUD_NODE_VM_DIR = getConfigValue("disk.store.cloud.node.vm_dir");
		CLOUD_SERVICE_USER = getConfigValue("disk.store.cloud.service.user");
		
		CACHE_LOCATION = getConfigValue("disk.store.cache.location");

	}

	public static ServiceConfiguration getInstance() {
		return instance;
	}

	private static Properties readConfigFile() {
		File cfgFile = new File(DEFAULT_CFG_FILENAME);
		Properties properties = new Properties();

		if (!cfgFile.exists()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Configuration file does not exists.");
		}

		FileReader reader = null;
		try {
			reader = new FileReader(cfgFile);
			properties.load(reader);
		} catch (IOException consumed) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"An error occured while reading configuration file");
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException consumed) {
					throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
							"An error occured while reading configuration file");
				}
			}
		}

		return properties;
	}

	private ShareType getShareType() {
		String type = getConfigValue("disk.store.share");
		return ShareType.valueOfIgnoreCase(type);
	}

	private DiskType getDiskType() {
		String value = getConfigValue("disk.store.iscsi.type");
		return DiskType.valueOfIgnoreCase(value);
	}

	private File getISCSIConfig() {
		String iscsiConf = getConfigValue("disk.store.iscsi.conf");
		File confHandler = new File(iscsiConf);
		File stratusConf = new File(System.getProperty(
				ISCSI_CONFIG_FILENAME_SYS_PARAM_NAME,
				DEFAULT_ISCSI_CONFIG_FILENAME));
		String includeConfig = "\ninclude " + stratusConf.getAbsolutePath()
				+ "\n";

		if (!confHandler.isFile()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Unable to find ISCSI configuration file.");
		}

		// Add include instruction in conf file in not
		if (!FileUtils
				.fileHasLine(confHandler, includeConfig.replace("\n", ""))) {
			FileUtils.appendToFile(confHandler, includeConfig);
		}

		return stratusConf;
	}

	private String getConfigValue(String key) {
		if (!CONFIGURATION.containsKey(key)) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Unable to retrieve configuration key: " + key);
		}

		return CONFIGURATION.getProperty(key);
	}

	private File getDiskLocation() {
		String diskStoreDir;
		if (SHARE_TYPE == ShareType.ISCSI) {
			diskStoreDir = getConfigValue("disk.store.iscsi.file.location");
		} else {
			diskStoreDir = getConfigValue("disk.store.nfs.location");
		}

		File diskStoreHandler = new File(diskStoreDir);

		// Don't check if we use LVM
		if (SHARE_TYPE == ShareType.ISCSI && ISCSI_DISK_TYPE == DiskType.LVM) {
			return diskStoreHandler;
		}

		if (!diskStoreHandler.isDirectory() || !diskStoreHandler.canWrite()
				|| !diskStoreHandler.canRead()) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Disk store must be readable and writable");
		}

		return diskStoreHandler;
	}

	private String getCommand(String configName) {
		String configValue = getConfigValue(configName);
		File exec = new File(configValue);

		if (!FileUtils.isExecutable(exec)) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					configValue
							+ " command does not exist or is not executable");
		}

		return configValue;
	}

	private String getLVMGroup() {
		String lvmGroup = getConfigValue("disk.store.lvm.device");

		checkLVMGroupExists(lvmGroup);

		return lvmGroup;
	}

	private int getUsersPerDisks() {
		String users = getConfigValue("disk.store.user_per_disk");
		int userNo = 0;

		try {
			userNo = Integer.parseInt(users);
		} catch (NumberFormatException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Unable to get user per disk value");
		}

		return userNo;
	}

	private void checkLVMGroupExists(String lvmGroup) {
		ProcessBuilder pb = new ProcessBuilder(VGDISPLAY_CMD, lvmGroup);

		ProcessUtils
				.execute(pb,
						"LVM Group does not exist. Please create it and restart pdisk service");
	}

}
