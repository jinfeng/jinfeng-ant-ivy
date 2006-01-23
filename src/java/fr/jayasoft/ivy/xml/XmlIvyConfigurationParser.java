/*
 * This file is subject to the license found in LICENCE.TXT in the root directory of the project.
 * 
 * #SNAPSHOT#
 */
package fr.jayasoft.ivy.xml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import fr.jayasoft.ivy.Ivy;
import fr.jayasoft.ivy.ModuleId;
import fr.jayasoft.ivy.url.URLHandlerRegistry;
import fr.jayasoft.ivy.util.Configurator;
import fr.jayasoft.ivy.util.Message;

/**
 * @author Hanin
 *
 */
public class XmlIvyConfigurationParser extends DefaultHandler {
	private Configurator _configurator;
    private List _configuratorTags = Arrays.asList(new String[] {"resolvers", "latest-strategies", "conflict-managers"});

    private Ivy _ivy;

    private String _defaultResolver;
    private String _defaultCM;
    private String _defaultLatest;
    private String _currentConfiguratorTag;

    public XmlIvyConfigurationParser(Ivy ivy) {
        _ivy = ivy;
	}

    public void parse(URL configuration) throws ParseException, IOException {
        _configurator = new Configurator();
        // put every type definition from ivy to configurator
        Map typeDefs = _ivy.getTypeDefs();
        for (Iterator iter = typeDefs.keySet().iterator(); iter.hasNext();) {
            String name = (String) iter.next();
            _configurator.typeDef(name, (Class)typeDefs.get(name));
        }
        
        doParse(configuration);
    }

    private void doParse(URL configuration) throws IOException, ParseException {
        InputStream stream = null;
        try {
            stream = URLHandlerRegistry.getDefault().openStream(configuration);
            SAXParserFactory.newInstance().newSAXParser().parse(
                stream,
                this);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            ParseException pe = new ParseException("failed to configure with "+configuration+": "+e.getMessage(), 0);
            pe.initCause(e);
            throw pe;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void parse(Configurator configurator, URL configuration) throws IOException, ParseException {
        _configurator = configurator;
        doParse(configuration);        
    }

    public void startElement(String uri, String localName, String qName, Attributes att) throws SAXException {
        // we first copy attributes in a Map to be able to modify them
        Map attributes = new HashMap();
        for (int i=0; i<att.getLength(); i++) {
            attributes.put(att.getQName(i), att.getValue(i));
        }
        
        try {
            if (_configurator.getCurrent() != null) {
                if ("macrodef".equals(_currentConfiguratorTag) && _configurator.getTypeDef(qName) != null) {
                    String name = (String)attributes.get("name");
                    if (name == null) {
                        attributes.put("name", "@{name}");
                    } else {
                        attributes.put("name", "@{name}-"+name);
                    }
                }
                if (attributes.get("ref") != null) {
                    if (attributes.size() != 1) {
                        throw new IllegalArgumentException("ref attribute should be the only one ! found "+attributes.size()+" in "+qName);
                    }
                    String name = (String)attributes.get("ref");
                    Object child = null;
                    if ("resolvers".equals(_currentConfiguratorTag)) {
                        child = _ivy.getResolver(name);
                        if (child == null) {
                            throw new IllegalArgumentException("unknown resolver "+name+": resolver should be defined before being referenced");
                        }
                    } else if ("latest-strategies".equals(_currentConfiguratorTag)) {
                        child = _ivy.getLatestStrategy(name);
                        if (child == null) {
                            throw new IllegalArgumentException("unknown latest strategy "+name+": latest strategy should be defined before being referenced");
                        }
                    } else if ("conflict-managers".equals(_currentConfiguratorTag)) {
                        child = _ivy.getConflictManager(name);
                        if (child == null) {
                            throw new IllegalArgumentException("unknown conflict manager "+name+": conflict manager should be defined before being referenced");
                        }
                    }
                    if (child == null) {
                        throw new IllegalArgumentException("bad reference "+name);
                    }
                    _configurator.addChild(qName, child);
                } else {
                    _configurator.startCreateChild(qName);
                    for (Iterator iter = attributes.keySet().iterator(); iter.hasNext();) {
                        String attName = (String)iter.next();
                        _configurator.setAttribute(attName, _ivy.substitute((String)attributes.get(attName)));
                    }
                }
            } else if ("typedef".equals(qName)) {
                String name = _ivy.substitute((String)attributes.get("name"));
                String className = _ivy.substitute((String)attributes.get("classname"));
                Class clazz = Class.forName(className);
                _ivy.typeDef(name, clazz);
                _configurator.typeDef(name, clazz);
            } else if ("property".equals(qName)) {
                String name = _ivy.substitute((String)attributes.get("name"));
                String value = _ivy.substitute((String)attributes.get("value"));
                String override = _ivy.substitute((String)attributes.get("override"));
                if (name == null) {
                    throw new IllegalArgumentException("missing attribute name on property tag");
                }
                if (value == null) {
                    throw new IllegalArgumentException("missing attribute value on property tag");
                }
                _ivy.setVariable(name, value, override == null ? true : Boolean.valueOf(override).booleanValue());
            } else if ("properties".equals(qName)) {
                String propFilePath = _ivy.substitute((String)attributes.get("file"));
                String override = _ivy.substitute((String)attributes.get("override"));
                try {
                    Message.verbose("loading properties: "+propFilePath);
                    _ivy.loadProperties(new File(propFilePath), override == null ? true : Boolean.valueOf(override).booleanValue());
                } catch (Exception ex) {
                    Message.verbose("failed to load properties as file: trying as url: "+propFilePath);
                    _ivy.loadProperties(new URL(propFilePath), override == null ? true : Boolean.valueOf(override).booleanValue());
                }
            } else if ("include".equals(qName)) {
                String propFilePath = _ivy.substitute((String)attributes.get("file"));
                File incFile = new File(propFilePath);
                Map variables = new HashMap(_ivy.getVariables());
                try {
                    if (incFile.exists()) {
                        Message.verbose("including file: "+propFilePath);
                        _ivy.setConfigurationVariables(incFile);
                        new XmlIvyConfigurationParser(_ivy).parse(_configurator, incFile.toURL());
                    } else {
                        Message.verbose("including url: "+propFilePath);
                        URL url = new URL(propFilePath);
                        _ivy.setConfigurationVariables(url);
                        new XmlIvyConfigurationParser(_ivy).parse(_configurator, url);
                    }
                } finally {
                    _ivy.setVariables(variables);
                }
            } else if ("conf".equals(qName)) {
                String cache = (String)attributes.get("defaultCache");
                if (cache != null) {
                    _ivy.setDefaultCache(new File(_ivy.substitute(cache)));
                }
                String validate = (String)attributes.get("validate");
                if (validate != null) {
                    _ivy.setValidate(Boolean.valueOf(_ivy.substitute(validate)).booleanValue());
                }
                String up2d = (String)attributes.get("checkUpToDate");
                if (up2d != null) {
                    _ivy.setCheckUpToDate(Boolean.valueOf(_ivy.substitute(up2d)).booleanValue());
                }
                String cacheIvyPattern = (String)attributes.get("cacheIvyPattern");
                if (cacheIvyPattern != null) {
                    _ivy.setCacheIvyPattern(_ivy.substitute(cacheIvyPattern));
                }
                String cacheArtPattern = (String)attributes.get("cacheArtifactPattern");
                if (cacheArtPattern != null) {
                    _ivy.setCacheArtifactPattern(_ivy.substitute(cacheArtPattern));
                }
                String useRemoteConfig = (String)attributes.get("useRemoteConfig");
                if (useRemoteConfig != null) {
                    _ivy.setUseRemoteConfig(Boolean.valueOf(_ivy.substitute(useRemoteConfig)).booleanValue());
                }

                // we do not set following defaults here since no instances has been registered yet
                _defaultResolver = (String)attributes.get("defaultResolver");
                _defaultCM = (String)attributes.get("defaultConflictManager");
                _defaultLatest = (String)attributes.get("defaultLatestStrategy");

            } else if (_configuratorTags.contains(qName)) {
                _currentConfiguratorTag = qName;
                _configurator.setRoot(_ivy);
            } else if ("macrodef".equals(qName)) {
                _currentConfiguratorTag = qName;
                Configurator.MacroDef macrodef = _configurator.startMacroDef((String)attributes.get("name"));
                macrodef.addAttribute("name", null);
            } else if ("module".equals(qName)) {
                String organisation = _ivy.substitute((String)attributes.get("organisation"));
                String module = _ivy.substitute((String)attributes.get("name"));
                String resolver = _ivy.substitute((String)attributes.get("resolver"));
                _ivy.addModuleConfiguration(new ModuleId(organisation, module), resolver);
            }
        } catch (Exception ex) {
            throw new SAXException("problem in config file", ex);
        }
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (_configurator.getCurrent() != null) {
            if (_configuratorTags.contains(qName) && _configurator.getDepth() == 1) {
                _configurator.clear();
                _currentConfiguratorTag = null;
            } else if ("macrodef".equals(qName) && _configurator.getDepth() == 1) {
                _configurator.endMacroDef();
                _currentConfiguratorTag = null;
            } else {
                _configurator.endCreateChild();
            }
        }
    }
    
    public void endDocument() throws SAXException {
        if (_defaultResolver != null) {
            _ivy.setDefaultResolver(_ivy.substitute(_defaultResolver));
        }
        if (_defaultCM != null) {
            _ivy.setDefaultConflictManager(_ivy.getConflictManager(_ivy.substitute(_defaultCM)));
        }
        if (_defaultLatest != null) {
            _ivy.setDefaultLatestStrategy(_ivy.getLatestStrategy(_ivy.substitute(_defaultLatest)));
        }
    }
}
