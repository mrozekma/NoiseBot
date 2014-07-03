package main;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeSet;
import java.util.Vector;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.BasicHttpParams;

import panacea.Panacea;

import debugging.Log;

public class Git {
	private static final String GITHUB_URI = "https://github.com/mrozekma/NoiseBot";
	private static final int GITHUB_SIGNAL_PORT = 41933;

	public static class SyncException extends RuntimeException {
		public SyncException(String msg) {super(msg);}
		public SyncException(Exception e) {super(e);}
	}

	public static class Revision {
		private String hash;
		private String author;
		private String description;

		public Revision(String hash) {this(hash, null, null);}
		public Revision(String hash, String author, String description) {
			this.hash = hash;
			this.author = author;
			this.description = description;
		}

		public String getHash() {return this.hash;}
		public String getAuthor() {return this.author;}
		public String getDescription() {return this.description != null ? this.description : "";}

		@Override public String toString() {return this.getHash() + " by "+ getAuthor() + " -- " + this.getDescription();}
		@Override public boolean equals(Object other) {
			return (other instanceof Revision) && (((Revision)other).getHash().equals(this.getHash()));
		}
	}

	public static String revisionLink(Revision rev) {
		return String.format("%s/commit/%s", Git.GITHUB_URI, rev.getHash());
	}

	public static String diffLink(Revision fromRev, Revision toRev) {
		return String.format("%s/compare/%s...%s", Git.GITHUB_URI, fromRev.getHash(), toRev.getHash());
	}

	public static Revision head() {
		return diff(null, null)[0];
	}

	public static Revision[] diff(String fromRev, String toRev) {
		final String from = fromRev != null ? fromRev : "HEAD~1";
		final String to = toRev != null ? toRev : "";
		final Vector<Revision> revs = new Vector<Revision>();
		try {
			final Process p = Runtime.getRuntime().exec("git log --format=format:%h:%an:%s " + from + ".." + to);
			final Scanner s = new Scanner(p.getInputStream());
			while(s.hasNext()) {
				String[] parts = s.nextLine().split(":", 3);
				revs.add(new Revision(parts[0], parts[1], parts[2]));
			}
		} catch(IOException e) {
			Log.e(e);
		}

		return revs.toArray(new Revision[0]);
	}

	public static boolean pull() {
		try {
			final int rtn = Runtime.getRuntime().exec("git pull").waitFor();
			if(rtn == 0) {
				return true;
			}
			Log.e("Unexpected return code from git: %d", rtn);
		} catch(IOException e) {
			Log.e(e);
		} catch(InterruptedException e) {
			Log.e(e);
		}
		return false;
	}

	public static File[] getFiles(Revision from, Revision to) throws IOException {return getFiles(from.getHash(), to.getHash());}
	public static File[] getFiles(String from, String to) throws IOException {
		Vector<File> files = new Vector<File>();
		final Process p = Runtime.getRuntime().exec("git diff --name-only " + from + ".." + to);
		final Scanner s = new Scanner(p.getInputStream());
		while(s.hasNext())
			files.add(new File(s.next()));
		return files.toArray(new File[0]);
	}

	public static boolean branchExists(String branch) {
		try {
			final Process p = Runtime.getRuntime().exec("git branch");
			final Scanner s = new Scanner(p.getInputStream());
			while(s.hasNext()) {
				final String line = s.nextLine();
				if(line.trim().equals(branch))
					return true;
			}
		} catch(IOException e) {
			Log.e(e);
		}

		return false;
	}

	private static String filterFilename(String filename) {
		return filename.substring(filename.indexOf("NoiseBot/") + "NoiseBot/".length());
	}

	public static String[] affectedModules(NoiseBot bot, String from, String to) throws SyncException {
		final Map<File, String> dependentFiles = new HashMap<File, String>();
		{
			final Map<String, NoiseModule> modules = bot.getModules();
			for(Map.Entry<String, NoiseModule> entry : modules.entrySet()) {
				dependentFiles.put(new File("src/modules", entry.getKey() + ".java"), entry.getKey());
				synchronized(NoiseBot.moduleFileDeps) {
					if(NoiseBot.moduleFileDeps.containsKey(entry.getKey())) {
						for(File f : NoiseBot.moduleFileDeps.get(entry.getKey())) {
							dependentFiles.put(f, entry.getKey());
						}
					}
				}
			}
		}

		final TreeSet<String> moduleNames = new TreeSet<String>();
		try {
			for(File f : getFiles(from, to)) {
				if(dependentFiles.containsKey(f)) {
					moduleNames.add(dependentFiles.get(f));
				}
			}
		} catch(IOException e) {
			throw new SyncException(e);
		}

		final String[] rtn = moduleNames.toArray(new String[0]);
		Arrays.sort(rtn);
		return rtn;
	}

	public static void startGithubListener(final String secret) {
		new Thread(new Runnable() {
			@Override public void run() {
				final ServerSocket server;
				try {
					server = new ServerSocket(GITHUB_SIGNAL_PORT);
				} catch(IOException e) {
					Log.e("Unable to listen for github updates");
					Log.e(e);
					return;
				}
				while(true) {
					try {
						final Socket socket = server.accept();
						synchronized(server) {
							new Thread(new Runnable() {
								@Override public void run() {
									try {
										Log.i("Received new Github alert from %s", socket.getRemoteSocketAddress());
										final DefaultHttpServerConnection conn = new DefaultHttpServerConnection();
										conn.bind(socket, new BasicHttpParams());
										final HttpEntityEnclosingRequest req = (HttpEntityEnclosingRequest)conn.receiveRequestHeader();
										conn.receiveRequestEntity(req);

										final Header[] headers = req.getHeaders("X-Hub-Signature");
										if(headers.length != 1) {
											Log.e("Signature headers: %d", headers.length);
											return;
										}

										String signature = headers[0].getValue();
										if(!signature.startsWith("sha1=")) {
											Log.e("Bad signature format: %s", signature);
											return;
										}
										signature = signature.substring("sha1=".length());

										final String payload;
										{
											final StringBuffer buffer = new StringBuffer();
											final Scanner s = new Scanner(req.getEntity().getContent());
											while(s.hasNextLine()) {
												buffer.append(s.nextLine());
											}
											payload = buffer.toString();
										}
										try {
											final Mac mac = Mac.getInstance("HmacSHA1");
											mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA1"));
											if(!signature.equals(Hex.encodeHexString(mac.doFinal(payload.getBytes())))) {
												Log.e("Bad signature: %s", signature);
												return;
											}
										} catch(NoSuchAlgorithmException e) {
											Log.e(e);
											return;
										} catch(InvalidKeyException e) {
											Log.e(e);
											return;
										}

										// Don't actually need the payload for anything
										// Just pull from github and try to sync
										// final JSONObject json = new JSONObject(payload);
										if(Git.pull()) {
											final int rtn = Runtime.getRuntime().exec("make").waitFor();
											if(rtn != 0) {
												Log.e("Unable to build new changes");
											}
											NoiseBot.syncAll();
										}
									} catch(InterruptedException e) {
										Log.e(e);
									} catch(IOException e) {
										Log.e(e);
									} catch(HttpException e) {
										Log.e(e);
									}
								}
							}).start();
						}
					} catch(IOException e) {}
				}
			}
		}).start();
	}
}
