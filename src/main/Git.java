package main;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.TreeSet;
import java.util.Vector;

public class Git {
	public static class SyncException extends RuntimeException {
		public SyncException(String msg) {super(msg);}
		public SyncException(Exception e) {super(e);}
	}
	
	public static class Revision {
		private String hash;
		private String description;

		public Revision(String hash) {this(hash, null);}
		public Revision(String hash, String description) {
			this.hash = hash;
			this.description = description;
		}

		public String getHash() {return this.hash;}
		public String getDescription() {return this.description != null ? this.description : "";}

		@Override public String toString() {return this.getHash().substring(0, 7) + " -- " + this.getDescription();}
		@Override public boolean equals(Object other) {
			return (other instanceof Revision) && (((Revision)other).getHash().equals(this.getHash()));
		}
	}

	public static Revision head() {
		return diff(null, null)[0];
	}

	public static Revision[] diff(String fromRev, String toRev) {
		final String from = fromRev != null ? fromRev : "HEAD~1";
		final String to = toRev != null ? toRev : "";
		final Vector<Revision> revs = new Vector<Revision>();
		try {
			final Process p = Runtime.getRuntime().exec("git log --format=oneline " + from + ".." + to);
			final Scanner s = new Scanner(p.getInputStream());
			while(s.hasNext()) {
				String[] parts = s.nextLine().split(" ", 2);
				revs.add(new Revision(parts[0], parts[1]));
			}
		} catch(IOException e) {
			e.printStackTrace();
		}

		return revs.toArray(new Revision[0]);
	}

	public static File[] getFiles(Revision from, Revision to) throws IOException {return getFiles(from.getHash(), to.getHash());}
	public static File[] getFiles(String from, String to) throws IOException {
		Vector<File>  files = new Vector<File>();
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
			e.printStackTrace();
		}
		
		return false;
	}
	
	public static void sync(String branch) throws SyncException {
		try {
			branch = branch.toLowerCase();
			if(branch.equals("master"))
				throw new SyncException("Can't sync to master");
			
			{
				boolean branchExists = false;
				final Process p = Runtime.getRuntime().exec("git branch");
				final Scanner s = new Scanner(p.getInputStream());
				while(s.hasNext()) {
					final String line = s.nextLine();
					if(line.trim().equals(branch))
						branchExists = true;
					if(line.charAt(0) == '*' && !line.equals("* master"))
						throw new SyncException("Not on master");
				}
				if(!branchExists)
					throw new SyncException("No branch named " + branch + " exists");
			}
			
			{
				boolean descendent = false;
				final Process p = Runtime.getRuntime().exec("git branch --contains master");
				final Scanner s = new Scanner(p.getInputStream());
				while(s.hasNext()) {
					if(s.nextLine().replaceFirst("\\*", "").trim().equals(branch)) {
						descendent = true;
						break;
					}
				}
				if(!descendent)
					throw new SyncException("Branch " + branch + " does not descend from master");
			}
			
			{
				final File[] files = getFiles("master", branch);
				for(File f : files) {
					final String filename = filterFilename(f.getCanonicalPath());
					if(filename.startsWith("src/") && !filename.startsWith("src/modules/"))
						throw new SyncException("Unable to dynamically switch branches -- core sources are modified");
					if(filename.startsWith("bin/") && !filename.startsWith("bin/modules/"))
						throw new SyncException("Unable to dynamically switch branches -- core classes are modified");
				}
			}
			
			try {
				Process p = Runtime.getRuntime().exec("git merge " + branch);
				int retCode = p.waitFor();
				if(retCode != 0)
					throw new SyncException("Merge returned code " + retCode);
				p = Runtime.getRuntime().exec("git branch -D " + branch);
				retCode = p.waitFor();
				if(retCode != 0)
					throw new SyncException("Branch returned code " + retCode);
			} catch(InterruptedException e) {
				throw new SyncException(e);
			}
		} catch(IOException e) {
			throw new SyncException(e);
		}
	}
	
	private static String filterFilename(String filename) {
		return filename.substring(filename.indexOf("NoiseBot/") + "NoiseBot/".length());
	}
	
	public static String[] affectedModules(String from, String to) throws SyncException {
		TreeSet<String> moduleNames = new TreeSet<String>();
		try {
			System.out.println(from + ".." + to);
			for(File f : getFiles(from, to)) {
				System.out.println(f);
				final String filename = filterFilename(f.getCanonicalPath());
				System.out.println(filename);
				if(filename.endsWith(".java")) {
					final String moduleName = f.getName().replace(".java", "");
					moduleNames.add(moduleName);
				}
			}
		} catch(IOException e) {
			throw new SyncException(e);
		}
		
		String[] rtn = moduleNames.toArray(new String[0]);
		Arrays.sort(rtn);
		return rtn;
	}
}
