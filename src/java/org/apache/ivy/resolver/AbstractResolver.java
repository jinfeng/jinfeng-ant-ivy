/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.ivy.resolver;

import java.io.File;
import java.util.Map;

import org.apache.ivy.Artifact;
import org.apache.ivy.DependencyDescriptor;
import org.apache.ivy.DependencyResolver;
import org.apache.ivy.Ivy;
import org.apache.ivy.IvyAware;
import org.apache.ivy.IvyNode;
import org.apache.ivy.LatestStrategy;
import org.apache.ivy.ModuleDescriptor;
import org.apache.ivy.ModuleRevisionId;
import org.apache.ivy.ResolveData;
import org.apache.ivy.ResolvedModuleRevision;
import org.apache.ivy.matcher.Matcher;
import org.apache.ivy.matcher.NoMatcher;
import org.apache.ivy.matcher.PatternMatcher;
import org.apache.ivy.namespace.NameSpaceHelper;
import org.apache.ivy.namespace.Namespace;
import org.apache.ivy.report.ArtifactDownloadReport;
import org.apache.ivy.report.DownloadReport;
import org.apache.ivy.report.DownloadStatus;
import org.apache.ivy.util.Message;


/**
 * This abstract resolver only provides handling for resolver name
 */
public abstract class AbstractResolver implements DependencyResolver, IvyAware, HasLatestStrategy {

    /**
     * True if parsed ivy files should be validated against xsd, false if they should not,
     * null if default behaviour should be used
     */
    private Boolean _validate = null;
    private String _name;
    private String _changingPattern;
    private String _changingMatcherName = PatternMatcher.EXACT_OR_REGEXP;
    
    private Ivy _ivy;

    /**
     * The latest strategy to use to find latest among several artifacts
     */
    private LatestStrategy _latestStrategy;

    private String _latestStrategyName;

    /**
     * The namespace to which this resolver belongs
     */
    private Namespace _namespace;

    private String _namespaceName;

    public Ivy getIvy() {
        return _ivy;
    }    

    public void setIvy(Ivy ivy) {
        _ivy = ivy;
    }
    
    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }


    /**
     * this method should remove sensitive information from a location to be displayed in a log
     * @param name location
     * @return location with sensitive data replaced by stars
     */
    public String hidePassword(String name) {
        return name;
    }

    protected boolean doValidate(ResolveData data) {
        if (_validate != null) {
            return _validate.booleanValue();
        } else {
            return data.isValidate();
        }
    }

    public boolean isValidate() {
        return _validate == null ? true: _validate.booleanValue();
    }
    

    public void setValidate(boolean validate) {
        _validate = Boolean.valueOf(validate);
    }


    protected void checkInterrupted() {
		if (_ivy != null) {
			_ivy.checkInterrupted();
		}
	}

    public void reportFailure() {
        Message.verbose("no failure report implemented by "+getName());
    }

    public void reportFailure(Artifact art) {
        Message.verbose("no failure report implemented by "+getName());
    }

    public String[] listTokenValues(String token, Map otherTokenValues) {
    	return new String[0];
    }
    public OrganisationEntry[] listOrganisations() {
        return new OrganisationEntry[0];
    }
    public ModuleEntry[] listModules(OrganisationEntry org) {
        return new ModuleEntry[0];
    }
    public RevisionEntry[] listRevisions(ModuleEntry module) {
        return new RevisionEntry[0];
    }

    public String toString() {
        return getName();
    }
    public void dumpConfig() {
        Message.verbose("\t"+getName()+" ["+getTypeName()+"]");
        Message.debug("\t\tchangingPattern: "+getChangingPattern());
        Message.debug("\t\tchangingMatcher: "+getChangingMatcherName());
    }

    public String getTypeName() {
        return getClass().getName();
    }
    /**
     * Default implementation actually download the artifact
     * Subclasses should overwrite this to avoid the download
     */
    public boolean exists(Artifact artifact) {
        DownloadReport dr = download(new Artifact[] {artifact}, getIvy(), getIvy().getDefaultCache(), true);
        ArtifactDownloadReport adr = dr.getArtifactReport(artifact);
        return adr.getDownloadStatus() != DownloadStatus.FAILED;
    }
    
    public DownloadReport download(Artifact[] artifacts, Ivy ivy, File cache) {
    	return download(artifacts, ivy, cache, false);
    }

    public LatestStrategy getLatestStrategy() {        
        if (_latestStrategy == null) {
            if (getIvy() != null) {
                if (_latestStrategyName != null && !"default".equals(_latestStrategyName)) {
                    _latestStrategy = getIvy().getLatestStrategy(_latestStrategyName);
                    if (_latestStrategy == null) {
                        Message.error("unknown latest strategy: "+_latestStrategyName);
                        _latestStrategy = getIvy().getDefaultLatestStrategy();
                    }
                } else {
                    _latestStrategy = getIvy().getDefaultLatestStrategy();
                    Message.debug(getName()+": no latest strategy defined: using default");
                }
            } else {
                throw new IllegalStateException("no ivy instance found: impossible to get a latest strategy without ivy instance");
            }
        }
        return _latestStrategy;
    }
    

    public void setLatestStrategy(LatestStrategy latestStrategy) {
        _latestStrategy = latestStrategy;
    }    

    public void setLatest(String strategyName) {
        _latestStrategyName = strategyName;
    }    
    
    public String getLatest() {
        if (_latestStrategyName == null) {
            _latestStrategyName = "default";
        }
        return _latestStrategyName;
    }

    public Namespace getNamespace() {        
        if (_namespace == null) {
            if (getIvy() != null) {
                if (_namespaceName != null) {
                    _namespace = getIvy().getNamespace(_namespaceName);
                    if (_namespace == null) {
                        Message.error("unknown namespace: "+_namespaceName);
                        _namespace = getIvy().getSystemNamespace();
                    }
                } else {
                    _namespace = getIvy().getSystemNamespace();
                    Message.debug(getName()+": no namespace defined: using system");
                }
            } else {
                Message.verbose(getName()+": no namespace defined nor ivy instance: using system namespace");
                _namespace = Namespace.SYSTEM_NAMESPACE;
            }
        }
        return _namespace;
    }
    
    public void setNamespace(String namespaceName) {
        _namespaceName = namespaceName;
    }    

    
    // Namespace conversion methods
    protected ModuleDescriptor toSystem(ModuleDescriptor md) {
        return NameSpaceHelper.toSystem(md, getNamespace());
    }

    protected Artifact fromSystem(Artifact artifact) {
        return NameSpaceHelper.transform(artifact, getNamespace().getFromSystemTransformer());
    }

    protected Artifact toSystem(Artifact artifact) {
        return NameSpaceHelper.transform(artifact, getNamespace().getToSystemTransformer());
    }

    protected ResolvedModuleRevision toSystem(ResolvedModuleRevision rmr) {
        return NameSpaceHelper.toSystem(rmr, getNamespace());
    }

    protected ModuleRevisionId toSystem(ModuleRevisionId resolvedMrid) {
        return getNamespace().getToSystemTransformer().transform(resolvedMrid);
    }

    protected DependencyDescriptor fromSystem(DependencyDescriptor dd) {
        return NameSpaceHelper.transform(dd, getNamespace().getFromSystemTransformer(), true);
    }

    protected IvyNode getSystemNode(ResolveData data, ModuleRevisionId resolvedMrid) {
        return data.getNode(toSystem(resolvedMrid));
    }

    protected ResolvedModuleRevision findModuleInCache(ResolveData data, ModuleRevisionId mrid) {
        ResolvedModuleRevision moduleFromCache = data.getIvy().findModuleInCache(toSystem(mrid), data.getCache(), doValidate(data));
        if (moduleFromCache == null) {
            return null;
        }
        if ((getName() == null ? 
                moduleFromCache.getResolver().getName() == null : 
                    moduleFromCache.getResolver() == null ? false : 
                        getName().equals(moduleFromCache.getResolver().getName()))) {
            return moduleFromCache;
        } else {
            Message.debug("found module in cache but with a different resolver: discarding: "+moduleFromCache);
            return null;
        }
    }

    public String getChangingMatcherName() {
        return _changingMatcherName;
    }

    public void setChangingMatcher(String changingMatcherName) {
        _changingMatcherName = changingMatcherName;
    }

    public String getChangingPattern() {
        return _changingPattern;
    }

    public void setChangingPattern(String changingPattern) {
        _changingPattern = changingPattern;
    }

    public Matcher getChangingMatcher() {
        if (_changingPattern == null) {
            return NoMatcher.getInstance();
        }
        PatternMatcher matcher = _ivy.getMatcher(_changingMatcherName);
        if (matcher == null) {
            throw new IllegalStateException("unknown matcher '"+_changingMatcherName+"'. It is set as changing matcher in "+this);
        }
        return matcher.getMatcher(_changingPattern);
    }

}