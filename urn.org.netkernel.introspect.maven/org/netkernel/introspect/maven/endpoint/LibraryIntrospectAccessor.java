package org.netkernel.introspect.maven.endpoint;

import java.util.HashMap;

import org.netkernel.layer0.boot.BootUtils;
import org.netkernel.layer0.nkf.INKFRequest;
import org.netkernel.layer0.nkf.INKFRequestContext;
import org.netkernel.layer0.representation.IHDSNode;
import org.netkernel.layer0.representation.IHDSNodeList;
import org.netkernel.layer0.representation.impl.HDSBuilder;
import org.netkernel.module.standard.endpoint.StandardAccessorImpl;

public class LibraryIntrospectAccessor extends StandardAccessorImpl
{
	private static final String COM_BASE="urn.com.ten60.core";
	private static final String ORG_BASE="urn.org.netkernel";
	private static final String LIB_BASE="urn.org.netkernel.expanded.lib";

	public LibraryIntrospectAccessor()
	{	this.declareThreadSafe();		
	}

	@Override
	public void onSource(INKFRequestContext aContext) throws Exception
	{
		String installPath=BootUtils.getInstallPath(aContext.getKernelContext().getKernel().getConfiguration());
		String libPath=installPath+"lib/";
		
		//Find kernel and core libraries
		HDSBuilder b=new HDSBuilder();
		b.pushNode("fls");
		b.addNode("root", libPath);
		b.addNode("filter", ".*.jar");
		b.addNode("uri", "true");
		
		INKFRequest req=aContext.createRequest("active:fls");
		req.addArgumentByValue("operator", b.getRoot());
		req.setRepresentationClass(IHDSNode.class);
		IHDSNode rep=(IHDSNode)aContext.issueRequest(req);
		
		//Parse and place into a tree structure
		b=new HDSBuilder();
		String base=COM_BASE+".";
		String[] baseA=base.split("\\.");
		for(int i=0; i<baseA.length; i++)
		{	b.pushNode(baseA[i], null);			
		}
		for(IHDSNode n : rep.getNodes("//res"))
		{	String raw=(String)n.getValue();
			String s=raw.replaceAll("\\.jar", "");
			String[] sa=s.split("-");
			String version=sa[1];
			String name=sa[0];
			name=name.substring(base.length());
			b.pushNode("artifact", name);
			b.addNode(name, null);
			b.addNode(name+version, null);
			b.addNode(raw, null);
			b.addNode("group", COM_BASE);
			b.addNode("name", name);
			b.addNode("version", version);
			b.addNode("jar", raw);
			b.addNode("fullname", s);
			b.addNode("uri", n.getFirstValue("uri"));
			IHDSNode deps=aContext.source("res:/resources/static-dependencies.xml", IHDSNode.class);
			IHDSNodeList d=deps.getNodes("//"+name);
			if(d.size()>0)
			{	b.pushNode("dependencies");
				for(int i=0; i<d.size(); i++)
				{	IHDSNode m=d.get(i);
					b.pushNode("dependency");
					b.addNode("groupId", COM_BASE);
					b.addNode("artifactId", m.getValue());
					b.addNode("version", "[1.1.1,)");
					b.popNode();
				}
				b.popNode();
			}
			b.popNode();
		}
		
		//Find system modules
		libPath=installPath+"modules/";
		
		HDSBuilder b2=new HDSBuilder();
		b2.pushNode("fls");
		b2.addNode("root", libPath);
		b2.addNode("filter", ".*urn\\.org\\.netkernel\\..*?.jar");
		b2.addNode("uri", "true");
		
		req=aContext.createRequest("active:fls");
		req.addArgumentByValue("operator", b2.getRoot());
		req.setRepresentationClass(IHDSNode.class);
		rep=(IHDSNode)aContext.issueRequest(req);

		//Parse and place into the tree
		libPath=installPath+"lib/expanded/";
		base=ORG_BASE+".";
		baseA=base.split("\\.");
		IHDSNode urnnode=b.getRoot().getFirstNode("/urn");
		b.setCursor(urnnode);
		for(int i=1; i<baseA.length; i++)
		{	b.pushNode(baseA[i], null);			
		}
		HDSBuilder b3=new HDSBuilder();
		b3.pushNode("libs");
		for(IHDSNode n : rep.getNodes("//res"))
		{	String raw=(String)n.getValue();
			String s=raw.replaceAll("\\.jar", "");
			String[] sa=s.split("-");
			String version=sa[1];
			String fullname=sa[0];
			String name=fullname.substring(base.length());
			String uri=(String)n.getFirstValue("uri");
			//Imported Dependencies
			IHDSNode module=aContext.source("jar:"+uri+"!/module.xml", IHDSNode.class);
			IHDSNode exports=module.getFirstNode("/module/system/classloader/exports");
			if(exports!=null)
			{	//Only add the module to maven if it actually exports a classpath
				b.pushNode("artifact", name);
					b.addNode(name, null);
					b.addNode(name+version, null);
					b.addNode(raw, null);
					b.addNode("name", name);
					b.addNode("group", ORG_BASE);
					b.addNode("version", version);
					b.addNode("jar", raw);
					b.addNode("fullname", s);
					b.addNode("uri", uri);
					//Static Dependencies
					b.pushNode("dependencies");
						b.pushNode("dependency");
						b.addNode("groupId", COM_BASE);
						b.addNode("artifactId", "netkernel.api");
						b.addNode("version", "[1.1.1,)");
						b.popNode();
						b.pushNode("dependency");
						b.addNode("groupId", COM_BASE);
						b.addNode("artifactId", "netkernel.impl");
						b.addNode("version", "[1.1.1,)");
						b.popNode();
						b.pushNode("dependency");
						b.addNode("groupId", COM_BASE);
						b.addNode("artifactId", "layer0");
						b.addNode("version", "[1.1.1,)");
						b.popNode();
						b.pushNode("dependency");
						b.addNode("groupId", COM_BASE);
						b.addNode("artifactId", "module.standard");
						b.addNode("version", "[1.1.1,)");
						b.popNode();
						b.pushNode("dependency");
						b.addNode("groupId", COM_BASE);
						b.addNode("artifactId", "cache.se");
						b.addNode("version", "[1.1.1,)");
						b.popNode();
						//Find any imports
						IHDSNodeList imports=module.getNodes("//import");
						HashMap added=new HashMap();
						for(int i=0; i<imports.size(); i++)
						{	IHDSNode imp=imports.get(i);
							String is=((String)imp.getFirstValue("uri")).replaceAll(":", ".");
							if(is.startsWith(base))
							{	is=is.substring(base.length());
								if(added.get(is)==null)
								{	b.pushNode("dependency");
										b.addNode("groupId", ORG_BASE);
										b.addNode("artifactId", is);
										b.addNode("version", "[1.1.1,)");
									b.popNode();
									added.put(is, is);
								}
							}
						}
						//Find any expanded jar libraries
						b2=new HDSBuilder();
						b2.pushNode("fls");
						b2.addNode("root", libPath);
						String filter = ".*?"+fullname.replaceAll("\\.","\\\\.")+"-"+version.replaceAll("\\.","\\\\.")+".*.jar";
						b2.addNode("filter", filter);
						b2.addNode("uri", "true");
						
						req=aContext.createRequest("active:fls");
						req.addArgumentByValue("operator", b2.getRoot());
						req.setRepresentationClass(IHDSNode.class);
						rep=(IHDSNode)aContext.issueRequest(req);
						
						for(IHDSNode n2 : rep.getNodes("//res"))
						{
							String id=((String)n2.getValue()).replaceAll("\\.jar","");
							b.pushNode("dependency");
							b.addNode("groupId", LIB_BASE);
							b.addNode("artifactId", id);
							b.addNode("version", version);
							b.popNode();
							b3.pushNode("lib");
								b3.addNode("artifactId", id);
								b3.addNode("version", version);
								b3.importNode(n2);
							b3.popNode();
						}
					b.popNode();
				b.popNode();
			}
		}
		//Process discovered expanded library artifacts
		b.setCursor(b.getRoot().getFirstNode("/urn/org/netkernel"));  //Reset cursor to the /urn/org/netkernel path and add the expanded/lib/ path
		b.pushNode("expanded");
		b.pushNode("lib");
		for(IHDSNode n : b3.getRoot().getNodes("//lib"))
		{	String name=(String)n.getFirstValue("artifactId");
			String version=(String)n.getFirstValue("version");
			String raw=LIB_BASE+"."+name+"-"+version+".jar";
			b.pushNode("artifact", name);
				b.addNode(name, null);
				b.addNode(name+version, null);
				b.addNode(raw, null);
				b.addNode("group", LIB_BASE);
				b.addNode("name", name);
				b.addNode("version", version);
				b.addNode("jar", name+".jar");
				b.addNode("fullname", ORG_BASE+"."+name);
				b.addNode("uri", n.getFirstValue("res/uri"));
			b.popNode();
		}
		aContext.createResponseFrom(b.getRoot());
	}
}

