package org.tigris.subversion.subclipse.core.history;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.eclipse.core.resources.IResource;
import org.tigris.subversion.subclipse.core.ISVNLocalResource;
import org.tigris.subversion.subclipse.core.ISVNRepositoryLocation;
import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.subclipse.core.SVNProviderPlugin;
import org.tigris.subversion.subclipse.core.resources.SVNWorkspaceRoot;
import org.tigris.subversion.svnclientadapter.ISVNClientAdapter;
import org.tigris.subversion.svnclientadapter.ISVNProperty;
import org.tigris.subversion.svnclientadapter.SVNClientException;
import org.tigris.subversion.svnclientadapter.SVNUrl;

public class TagManager {
	private ArrayList aliases = new ArrayList();
	
	public TagManager(IResource resource) {
		Alias[] aliasArray = getTags(resource);
		for (int i = 0; i < aliasArray.length; i++) aliases.add(aliasArray[i]);
	}
	
	public TagManager(SVNUrl url) {
		Alias[] aliasArray = getTags(url);
		for (int i = 0; i < aliasArray.length; i++) aliases.add(aliasArray[i]);
	}
	
	public Alias[] getTags(int revision) {
		ArrayList revisionAliases = new ArrayList();
		Iterator iter = aliases.iterator();
		while (iter.hasNext()) {
			Alias alias = (Alias)iter.next();
			if (alias.getRevision() >= revision) {
				revisionAliases.add(alias);
			}
		}
		Alias[] aliasArray = new Alias[revisionAliases.size()];
		revisionAliases.toArray(aliasArray);
		for (int i = 0; i < aliasArray.length; i++) aliases.remove(aliasArray[i]);
		return aliasArray;
	}
	
	public Alias[] getTagTags() {
		ArrayList tags = new ArrayList();
		Iterator iter = aliases.iterator();
		while (iter.hasNext()) {
			Alias tag = (Alias)iter.next();
			if (!tag.isBranch()) {
				tags.add(tag);
			}
		}		
		Alias[] tagArray = new Alias[tags.size()];
		tags.toArray(tagArray);
		return tagArray;
	}
	
	public Alias[] getBranchTags() {
		ArrayList branches = new ArrayList();
		Iterator iter = aliases.iterator();
		while (iter.hasNext()) {
			Alias branch = (Alias)iter.next();
			if (branch.isBranch()) {
				branches.add(branch);
			}
		}		
		Alias[] branchArray = new Alias[branches.size()];
		branches.toArray(branchArray);
		return branchArray;
	}
	
	public Alias getTag(String revisionNamePathBranch, String tagUrl) {
		boolean branch = false;
		Alias alias = null;
		int index = revisionNamePathBranch.indexOf(",");
		if (index == -1) return null;
		String rev = revisionNamePathBranch.substring(0, index);
		int revision;
		try {
			int revNo = Integer.parseInt(rev);
			revision = revNo;			
		} catch (Exception e) { return null; }
		revisionNamePathBranch = revisionNamePathBranch.substring(index + 1);
		index = revisionNamePathBranch.indexOf(",");
		String name;
		String relativePath = null;
		if (index == -1) name = revisionNamePathBranch;
		else {
			name = revisionNamePathBranch.substring(0, index);
			if (revisionNamePathBranch.length() > index + 1) {
				revisionNamePathBranch = revisionNamePathBranch.substring(index + 1);
				index = revisionNamePathBranch.indexOf(",");
				if (index == -1)
					relativePath = revisionNamePathBranch;
				else {
					relativePath = revisionNamePathBranch.substring(0, index);
					if (revisionNamePathBranch.length() > index + 1)
						branch = revisionNamePathBranch.substring(index + 1).equalsIgnoreCase("branch"); //$NON-NLS-1$
				}
			}
		}
		alias = new Alias(revision, name, relativePath, tagUrl);
		alias.setBranch(branch);
		return alias;
	}

	public static String getTagsAsString(Alias[] aliases) {
		if (aliases == null) return "";
		StringBuffer stringBuffer = new StringBuffer();
		for (int i = 0; i < aliases.length; i++) {
			if (i != 0) stringBuffer.append(", ");
			stringBuffer.append(aliases[i].getName());
		}
		return stringBuffer.toString();
	}
	
	public static String transformUrl(IResource resource, Alias alias) {
		String aliasUrl = alias.getUrl();
		String a;
        ISVNLocalResource svnResource = SVNWorkspaceRoot.getSVNResourceFor(resource);
        ISVNRepositoryLocation repository = svnResource.getRepository();
		if (svnResource.getUrl().toString().length() <= aliasUrl.length()) 
			a = "";
		else
			a = svnResource.getUrl().toString().substring(aliasUrl.length());
		String b = repository.getUrl().toString();
		String c;
		if (alias.getRelativePath() == null) c = "";
		else c = alias.getRelativePath();			
		return b + c + a;
	}
	
	public Alias[] getTags(IResource resource) {
		Alias[] aliases = getTags(resource, true);		
		Arrays.sort(aliases);
		return aliases;
	}
	
	private Alias[] getTags(IResource resource, boolean checkParents)  {
		ArrayList aliases = new ArrayList();
		ISVNLocalResource svnResource = SVNWorkspaceRoot.getSVNResourceFor(resource);
		try {
			if (svnResource.isManaged()) {
				ISVNProperty property = null;
				property = svnResource.getSvnProperty("subclipse:tags"); //$NON-NLS-1$
				if (property != null && property.getValue() != null) getTags(aliases, property.getValue(), svnResource.getUrl().toString());
				if (checkParents) {
					IResource checkResource = resource;
					while (checkResource.getParent() != null) {
						checkResource = checkResource.getParent();
						Alias[] parentAliases = getTags(checkResource, false);
						for (int i = 0; i < parentAliases.length; i++) {
							if (aliases.contains(parentAliases[i])) {
								Alias checkAlias = (Alias)aliases.get(aliases.indexOf(parentAliases[i]));
								if (parentAliases[i].getRevision() < checkAlias.getRevision()) {
									aliases.remove(checkAlias);
									aliases.add(parentAliases[i]);
								}
							} else aliases.add(parentAliases[i]);
						}
					}
				}
			}
		} catch (SVNException e) {
		}
		Alias[] aliasArray = new Alias[aliases.size()];
		aliases.toArray(aliasArray);
		return aliasArray;
	}
	
	public Alias[] getTags(SVNUrl url) {
		Alias[] aliases = getTags(url, true);
		Arrays.sort(aliases);
		return aliases;
	}
	
	private Alias[] getTags(SVNUrl url, boolean checkParents)  {
		ArrayList aliases = new ArrayList();
		try {
			ISVNClientAdapter client = SVNProviderPlugin.getPlugin().createSVNClient();
			ISVNProperty property = null;
			property = client.propertyGet(url, "subclipse:tags");
			if (property != null && property.getValue() != null) {
				getTags(aliases, property.getValue(), url.toString());
			} else {
				if (url.getParent() != null && checkParents)
					return getTags(url.getParent(), checkParents);
			}
		} catch (SVNClientException e) {
		} catch (SVNException e) {
		}
		Alias[] aliasArray = new Alias[aliases.size()];
		aliases.toArray(aliasArray);
		return aliasArray;
	}
	
	private void getTags(ArrayList aliases, String propertyValue, String url) {
		StringReader reader = new StringReader(propertyValue);
		BufferedReader bReader = new BufferedReader(reader);
		try {
			String line = bReader.readLine();
			while (line != null) {
				Alias alias = getTag(line, url);
				if (aliases.contains(alias)) {
					Alias checkTag = (Alias)aliases.get(aliases.indexOf(alias));
					if (alias.getRevision() < checkTag.getRevision()) {
						aliases.remove(checkTag);
						aliases.add(alias);
					}					
				} else aliases.add(alias);
				line = bReader.readLine();
			}
			bReader.close();
		} catch (Exception e) {}
	}

}