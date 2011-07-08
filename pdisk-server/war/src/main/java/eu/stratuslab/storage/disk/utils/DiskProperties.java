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
package eu.stratuslab.storage.disk.utils;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper.States;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

import eu.stratuslab.storage.disk.main.PersistentDiskApplication;

public class DiskProperties {
	private ZooKeeper zk;

	// Property keys
	public static final String UUID_KEY = "uuid";
	public static final String DISK_OWNER_KEY = "owner";
	public static final String DISK_VISIBILITY_KEY = "visibility";
	public static final String DISK_CREATION_DATE_KEY = "created";

	public DiskProperties() {
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
					e.getMessage());
		}
	}

	public List<String> getDisks() {
		List<String> disks = null;
		try {
			disks = zk.getChildren(PersistentDiskApplication.ZK_ROOT_PATH,
					false);
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					e.getMessage());
		}

		return disks;
	}

	public void createNode(String path, String content) {
		if (diskExists(path)) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"Disk with same uuid already exists");
		}
		
		try {
			zk.create(path, content.getBytes(), Ids.OPEN_ACL_UNSAFE,
					CreateMode.PERSISTENT);
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					e.getMessage());
		}
	}

	public void deleteDiskProperties(String root) {
		try {
			List<String> tree = listSubTree(root);
			for (int i = tree.size() - 1; i >= 0; --i) {
				zk.delete(tree.get(i), -1);
			}
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					e.getMessage());
		}
	}

	private String getNode(String root) {
		String node = "";

		try {
			node = new String(zk.getData(root, false, null));
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					e.getMessage());
		}

		return node;
	}

	private List<String> listSubTree(String pathRoot) {
		Deque<String> queue = new LinkedList<String>();
		List<String> tree = new ArrayList<String>();
		List<String> children;

		queue.add(pathRoot);
		tree.add(pathRoot);

		while (true) {
			String node = queue.pollFirst();
			if (node == null) {
				break;
			}

			try {
				children = zk.getChildren(node, false);
			} catch (KeeperException e) {
				throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
						"ZooKeeper error: " + e.getMessage());
			} catch (InterruptedException e) {
				throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
						e.getMessage());
			}

			for (String child : children) {
				String childPath = node + "/" + child;
				queue.add(childPath);
				tree.add(childPath);
			}
		}
		return tree;
	}

	public Properties getDiskProperties(String root) {
		if (!diskExists(root)) {
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
					"Cannot load disk properties as it does not exists");
		}
		
		Properties properties = new Properties();
		List<String> tree = listSubTree(root);

		for (int i = tree.size() - 1; i >= 0; --i) {
			String key = PersistentDiskApplication.last(tree.get(i).split("/"));
			String content = getNode(tree.get(i));

			if (tree.get(i) == root) {
				key = UUID_KEY;
			}

			properties.put(key, content);
		}
		return properties;
	}

	private Boolean diskExists(String path) {
		try {
			return zk.exists(path, false) != null;
		} catch (KeeperException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					"ZooKeeper error: " + e.getMessage());
		} catch (InterruptedException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
					e.getMessage());
		}
	}

	public ZooKeeper getZooKeeper() {
		return zk;
	}
}