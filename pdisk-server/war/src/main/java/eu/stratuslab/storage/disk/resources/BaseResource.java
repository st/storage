/*
 Created as part of the StratusLab project (http://stratuslab.eu),
 co-funded by the European Commission under the Grant Agreement
 INSFO-RI-261552.

 Copyright (c) 2011, Centre National de la Recherche Scientifique (CNRS)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package eu.stratuslab.storage.disk.resources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooKeeper.States;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import eu.stratuslab.storage.disk.main.PersistentDiskApplication;
import eu.stratuslab.storage.disk.utils.DiskUtils;
import freemarker.template.Configuration;

public class BaseResource extends ServerResource {

	public enum DiskVisibility {
		PRIVATE, PUBLIC, RESTRICTED
	}

	protected static final String UUID_KEY = "uuid";
	protected static final String DISK_OWNER_KEY = "owner";
	protected static final String DISK_VISIBILITY_KEY = "visibility";

	protected static final Logger LOGGER = Logger.getLogger("org.restlet");

	public static final ZooKeeper ZK = initializeZooKeeper();

	private String username = "";

	private Configuration getFreeMarkerConfiguration() {
		return ((PersistentDiskApplication) getApplication())
				.getFreeMarkerConfiguration();
	}

	protected TemplateRepresentation createTemplateRepresentation(String tpl,
			Map<String, Object> info, MediaType mediaType) {

		Configuration freeMarkerConfig = getFreeMarkerConfiguration();

		return new TemplateRepresentation(tpl, freeMarkerConfig, info,
				mediaType);
	}

	protected Map<String, Object> createInfoStructure(String title) {

		Map<String, Object> info = new HashMap<String, Object>();

		// Add the standard base URL declaration.
		info.put("baseurl", getApplicationBaseUrl());

		// Add the title if appropriate.
		if (title != null && !"".equals(title)) {
			info.put("title", title);
		}

		// Add user name information
		info.put("username", getUsername());

		return info;
	}

	public String getUsername() {
		if (!username.isEmpty()) {
			return username;
		}

		ChallengeResponse cr = getRequest().getChallengeResponse();

		if (cr == null) {
			username = "UNKNOWN";
		} else {
			username = cr.getIdentifier();
		}

		return username;
	}

	protected String getApplicationBaseUrl() {
		return getRequest().getRootRef().toString();
	}

	public static List<String> getDisks() throws KeeperException,
			InterruptedException {
		List<String> disks = ZK.getChildren(
				PersistentDiskApplication.ZK_ROOT_PATH, false);

		return disks;
	}

	private static ZooKeeper initializeZooKeeper() {
		ZooKeeper zk = null;

		try {
			zk = new ZooKeeper(PersistentDiskApplication.ZK_ADDRESS,
					PersistentDiskApplication.ZK_PORT, null);
		} catch (Exception e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Unable to connect to ZooKeeper: " + e.getMessage());
		}

		try {
			while (zk.getState() == States.CONNECTING) {
				Thread.sleep(20);
			}

			if (zk.getState() != States.CONNECTED) {
				throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
						"Unable to connect to ZooKeeper");
			}
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"Unable to connect to ZooKeeper: " + e.getMessage());
		}

		try {
			if (zk.exists(PersistentDiskApplication.ZK_ROOT_PATH, false) == null) {
				zk.create(PersistentDiskApplication.ZK_ROOT_PATH,
						"pdisk".getBytes(), Ids.OPEN_ACL_UNSAFE,
						CreateMode.PERSISTENT);
			}
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		}

		return zk;
	}

	protected static String buildZkDiskPath(String uuid) {
		return PersistentDiskApplication.ZK_ROOT_PATH + "/" + uuid;
	}

	protected static void createZkNode(String path, String content) {
		try {
			ZK.create(path, content.getBytes(), Ids.OPEN_ACL_UNSAFE,
					CreateMode.PERSISTENT);
		} catch (KeeperException e) {
			LOGGER.severe("error inserting znode: " + e.getMessage());
		} catch (InterruptedException e) {
			LOGGER.severe("error inserting znode: " + e.getMessage());
		}
	}

	protected static void deleteRecursiveZkDiskProperties(String root) {
		try {
			List<String> tree = listZkSubTreeBFS(root);
			for (int i = tree.size() - 1; i >= 0; --i) {
				ZK.delete(tree.get(i), -1);
			}
		} catch (KeeperException e) {
			LOGGER.severe("error removing znode: " + e.getMessage());
		} catch (InterruptedException e) {
			LOGGER.severe("error removing znode: " + e.getMessage());
		}
	}

	protected static String getZkNode(String root) throws KeeperException,
			InterruptedException {
		return new String(ZK.getData(root, false, null));
	}

	protected static List<String> listZkSubTreeBFS(final String pathRoot)
			throws KeeperException, InterruptedException {
		Deque<String> queue = new LinkedList<String>();
		List<String> tree = new ArrayList<String>();

		queue.add(pathRoot);
		tree.add(pathRoot);

		while (true) {
			String node = queue.pollFirst();
			if (node == null) {
				break;
			}
			List<String> children = ZK.getChildren(node, false);
			for (final String child : children) {
				final String childPath = node + "/" + child;
				queue.add(childPath);
				tree.add(childPath);
			}
		}
		return tree;
	}

	protected static Properties loadZkProperties(String root)
			throws KeeperException, InterruptedException {
		Properties properties = new Properties();
		List<String> tree = listZkSubTreeBFS(root);

		for (int i = tree.size() - 1; i >= 0; --i) {
			String key = last(tree.get(i).split("/"));
			String content = getZkNode(tree.get(i));

			if (tree.get(i) == root) {
				key = UUID_KEY;
			}

			properties.put(key, content);
		}
		return properties;
	}

	protected static Boolean zkPathExists(String path) {
		try {
			return ZK.exists(path, false) != null;
		} catch (KeeperException e) {
			LOGGER.severe("ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			LOGGER.severe("ZooKeeper error: " + e.getMessage());
		}

		return false;
	}

	protected static <T> T last(T[] array) {
		return array[array.length - 1];
	}

	/**
	 * @return Current URL without the query string
	 */
	protected String getCurrentUrl() {
		return getCurrentUrlQueryString().replaceAll("\\?.*", "");
	}

	/**
	 * @return Current URL with the query string
	 */
	protected String getCurrentUrlQueryString() {
		return getRequest().getResourceRef().toString();
	}

	protected String getQueryString() {
		return getRequest().getResourceRef().getQuery();
	}

	protected Boolean hasQueryString(String key) {
		String queryString = getQueryString();
		return (queryString != null && queryString.equals(key));
	}

	protected void restartServer() {
		try {
			DiskUtils.restartServer();
		} catch (IOException e) {
			LOGGER.severe("error restarting server: " + e.getMessage());
		}
	}

	protected Boolean hasSuficientRightsToView(Properties properties) {
		// Is disk owner
		if (properties.get(DISK_OWNER_KEY).toString().equals(getUsername())) {
			return true;
		}

		DiskVisibility currentVisibility = fromStringDiskVisibility(properties.get(DISK_VISIBILITY_KEY).toString());
		// Is disk public
		if (currentVisibility.equals(DiskVisibility.PUBLIC)) {
			return true;
		}
		// TODO Is disk shared
		
		return false;
	}

	protected Boolean hasSuficientRightsToDelete(Properties properties) {
		// Need to be the owner to delete the disk
		return properties.get(DISK_OWNER_KEY).toString().equals(getUsername());
	}

	protected String toStringDiskVisibility(DiskVisibility visibility) {
		switch (visibility) {
		case PUBLIC:
			return "public";
		case RESTRICTED:
			return "restricted";
		default:
			return "private";
		}
	}

	protected DiskVisibility fromStringDiskVisibility(String visibility) {
		if ("public".equals(visibility)) {
			return DiskVisibility.PUBLIC;
		} else if ("restricted".equals(visibility)) {
			return DiskVisibility.RESTRICTED;
		} else {
			return DiskVisibility.PRIVATE;
		}
	}

}
