/*
 * This file is subject to the license found in LICENCE.TXT in the root directory of the project.
 * 
 * #SNAPSHOT#
 */
package fr.jayasoft.ivy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class can be used as the default implementation for DependencyDescriptor.
 * It implements required methods and enables to fill dependency information
 * with the addDependencyConfiguration method.
 * 
 * @author Xavier Hanin
 *
 */
public class DefaultDependencyDescriptor implements DependencyDescriptor {
	private static final Pattern SELF_FALLBACK_PATTERN = Pattern.compile("@(\\(.*\\))?");
    private ModuleRevisionId _revId;
    private Map _confs = new HashMap();
    private Map _artifactsIncludes = new HashMap(); // Map (String masterConf -> Collection(DependencyArtifactDescriptor))
    private Map _artifactsExcludes = new HashMap(); // Map (String masterConf -> Collection(DependencyArtifactDescriptor))
    private Set _extends = new HashSet();
    
    /**
     * Used to indicate that this revision must be used in case of conflicts, independently
     * of conflicts manager
     */
    private boolean _force;
    /**
     * Used to indicate that the dependency is a changing one, i.e. that ivy should not rely on the version to know if it can trust artifacts in cache
     */
    private boolean _changing; 
    private ModuleRevisionId _parentId;

    private boolean _transitive = true;

    public DefaultDependencyDescriptor(DependencyDescriptor dd, String revision) {
        _parentId = dd.getParentRevisionId();
        _revId = new ModuleRevisionId(dd.getDependencyId(), revision);
        _force = dd.isForce();
        _changing = dd.isChanging();
        _transitive = dd.isTransitive();
        String[] moduleConfs = dd.getModuleConfigurations();
        for (int i = 0; i < moduleConfs.length; i++) {
            _confs.put(moduleConfs[i], new ArrayList(Arrays.asList(dd.getDependencyConfigurations(moduleConfs[i]))));
            _artifactsExcludes.put(moduleConfs[i], new ArrayList(Arrays.asList(dd.getDependencyArtifactsExcludes(moduleConfs[i]))));
            _artifactsIncludes.put(moduleConfs[i], new ArrayList(Arrays.asList(dd.getDependencyArtifactsIncludes(moduleConfs[i]))));
        }
    }
    
    public DefaultDependencyDescriptor(ModuleDescriptor md, ModuleRevisionId mrid, boolean force, boolean changing, boolean transitive) {
        _parentId = md.getModuleRevisionId();
        _revId = mrid;
        _force = force;
        _changing = changing;
        _transitive = transitive;
    }
    
    public DefaultDependencyDescriptor(ModuleRevisionId mrid, boolean force) {
        this(mrid, force, false);
    }
    
    public DefaultDependencyDescriptor(ModuleRevisionId mrid, boolean force, boolean changing) {
        _revId = mrid;
        _force = force;
        _changing = changing;
    }
    
	public ModuleId getDependencyId() {
		return getDependencyRevisionId().getModuleId();
	}

	public ModuleRevisionId getDependencyRevisionId() {
		return _revId;
	}

	public String[] getModuleConfigurations() {
		return (String[]) _confs.keySet().toArray(new String[_confs.keySet().size()]);
	}

	public String[] getDependencyConfigurations(String moduleConfiguration) {
		List confs = (List)_confs.get(moduleConfiguration);
		List defConfs = (List)_confs.get("*");
		Collection ret = new LinkedHashSet();
		if (confs != null) {
		    ret.addAll(confs);
		}
		if (defConfs != null) {
		    ret.addAll(defConfs);
		}
        for (Iterator iter = ret.iterator(); iter.hasNext();) {
            String c = (String)iter.next();
            Matcher matcher = SELF_FALLBACK_PATTERN.matcher(c);
            if (matcher.matches()) {
                iter.remove();
                if (matcher.group(1) != null) {
                    ret.add(moduleConfiguration+matcher.group(1));
                } else {
                    ret.add(moduleConfiguration);
                }
                break;
            }
        }
        if (ret.contains("*")) {
            return new String[] {"*"};
        }
		return (String[])ret.toArray(new String[ret.size()]);
	}

	public String[] getDependencyConfigurations(String[] moduleConfigurations) {
		Set confs = new LinkedHashSet();
		for (int i = 0; i < moduleConfigurations.length; i++) {
			confs.addAll(Arrays.asList(getDependencyConfigurations(moduleConfigurations[i])));
		}
		if (confs.contains("*")) {
			return new String[] {"*"};
		}
		return (String[]) confs.toArray(new String[confs.size()]);
	}
    
    public DependencyArtifactDescriptor[] getDependencyArtifactsIncludes(String moduleConfiguration) {
        return getDependencyArtifacts(moduleConfiguration, _artifactsIncludes);
    }

    public DependencyArtifactDescriptor[] getDependencyArtifactsExcludes(String moduleConfiguration) {
        return getDependencyArtifacts(moduleConfiguration, _artifactsExcludes);
    }

    private DependencyArtifactDescriptor[] getDependencyArtifacts(String moduleConfiguration, Map artifactsMap) {
        if (artifactsMap.isEmpty()) { 
            return new DependencyArtifactDescriptor[0];
        }
        Collection artifacts = (Collection)artifactsMap.get(moduleConfiguration);
        Collection defArtifacts = (Collection)artifactsMap.get("*");
        Set ret = new HashSet();
        if (artifacts != null) {
            ret.addAll(artifacts);
        }
        if (defArtifacts != null) {
            ret.addAll(defArtifacts);
        }
        return (DependencyArtifactDescriptor[])ret.toArray(new DependencyArtifactDescriptor[ret.size()]);
    }

    public DependencyArtifactDescriptor[] getDependencyArtifactsIncludes(String[] moduleConfigurations) {
        Set artifacts = new HashSet();
        for (int i = 0; i < moduleConfigurations.length; i++) {
            artifacts.addAll(Arrays.asList(getDependencyArtifactsIncludes(moduleConfigurations[i])));
        }
        return (DependencyArtifactDescriptor[]) artifacts.toArray(new DependencyArtifactDescriptor[artifacts.size()]);
    }
    
    public DependencyArtifactDescriptor[] getDependencyArtifactsExcludes(String[] moduleConfigurations) {
        Set artifacts = new HashSet();
        for (int i = 0; i < moduleConfigurations.length; i++) {
            artifacts.addAll(Arrays.asList(getDependencyArtifactsExcludes(moduleConfigurations[i])));
        }
        return (DependencyArtifactDescriptor[]) artifacts.toArray(new DependencyArtifactDescriptor[artifacts.size()]);
    }
    
    public DependencyArtifactDescriptor[] getAllDependencyArtifactsIncludes() {
        return getAllDependencyArtifacts(_artifactsIncludes);
    }

    public DependencyArtifactDescriptor[] getAllDependencyArtifactsExcludes() {
        return getAllDependencyArtifacts(_artifactsExcludes);
    }

    private DependencyArtifactDescriptor[] getAllDependencyArtifacts(Map artifactsMap) {
        Set ret = new HashSet();
        for (Iterator it = artifactsMap.values().iterator(); it.hasNext();) {
            Collection artifacts = (Collection)it.next();
            ret.addAll(artifacts);
        }
        return (DependencyArtifactDescriptor[])ret.toArray(new DependencyArtifactDescriptor[ret.size()]);
    }

    public void addDependencyConfiguration(String masterConf, String depConf) {
        List confs = (List)_confs.get(masterConf);
        if (confs == null) {
            confs = new ArrayList();
            _confs.put(masterConf, confs);
        }
        if (!confs.contains(depConf)) {
            confs.add(depConf);
        }
    }
    
    public void addDependencyArtifactIncludes(String masterConf, DependencyArtifactDescriptor dad) {
        addDependencyArtifacts(masterConf, dad, _artifactsIncludes);
    }

    public void addDependencyArtifactExcludes(String masterConf, DependencyArtifactDescriptor dad) {
        addDependencyArtifacts(masterConf, dad, _artifactsExcludes);
    }

    private void addDependencyArtifacts(String masterConf, DependencyArtifactDescriptor dad, Map artifactsMap) {
        Collection artifacts = (Collection)artifactsMap.get(masterConf);
        if (artifacts == null) {
            artifacts = new ArrayList();
            artifactsMap.put(masterConf, artifacts);
        }
        artifacts.add(dad);
    }
    
    public void addExtends(String conf) {
        _extends.add(conf);
    }

    public String toString() {
        return "dependency: "+_revId+" "+_confs;
    }

    public boolean isForce() {
        return _force;
    }

    public ModuleRevisionId getParentRevisionId() {
        return _parentId;
    }

    public boolean isChanging() {
        return _changing;
    }

    public boolean isTransitive() {
        return _transitive;
    }

}
