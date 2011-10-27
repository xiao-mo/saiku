package org.saiku.plugin;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import mondrian.olap4j.SaikuMondrianHelper;

import org.olap4j.OlapConnection;
import org.pentaho.platform.api.engine.IConnectionUserRoleMapper;
import org.pentaho.platform.engine.core.system.PentahoSessionHolder;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.plugin.services.connections.mondrian.MDXConnection;
import org.saiku.datasources.connection.AbstractConnectionManager;
import org.saiku.datasources.connection.ISaikuConnection;
import org.saiku.datasources.connection.SaikuConnectionFactory;
import org.saiku.datasources.datasource.SaikuDatasource;

public class PentahoSecurityAwareConnectionManager extends AbstractConnectionManager {

	public static final String MDX_CONNECTION_MAPPER_KEY = "Mondrian-UserRoleMapper"; //$NON-NLS-1$

	private Map<String, ISaikuConnection> connections = new HashMap<String, ISaikuConnection>();

	private List<String> errorConnections = new ArrayList<String>();

	private Boolean userAware;

	@Override
	public void init() {
		this.connections = getAllConnections();
	}
	
	public void setUserAware(Boolean aware) {
		this.userAware = aware;
	}

	@Override
	protected ISaikuConnection getInternalConnection(String name, SaikuDatasource datasource) {
		ISaikuConnection con;
		//		System.out.println("LOAD CONNECTION::::::::::::" + name);
		if (userAware && PentahoSessionHolder.getSession().getName() != null) {
			name = name + "-" + PentahoSessionHolder.getSession().getName();
		}
		if (!connections.containsKey(name)) {
			con =  connect(name, datasource);
			if (con != null) {
				connections.put(name, con);
			} else {
				if (!errorConnections.contains(name)) {
					errorConnections.add(name);
				}
			}

		} else {
			con = connections.get(name);
		}

		try {
			con = applySecurity(con, datasource);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return con;
	}


	private ISaikuConnection applySecurity(ISaikuConnection con, SaikuDatasource datasource) throws Exception {
		if (con == null) {
			throw new IllegalArgumentException("Cannot apply Security to NULL connection object");
		}

		if (PentahoSystem.getObjectFactory().objectDefined(MDXConnection.MDX_CONNECTION_MAPPER_KEY)) {
			IConnectionUserRoleMapper mondrianUserRoleMapper = PentahoSystem.get(IConnectionUserRoleMapper.class, MDXConnection.MDX_CONNECTION_MAPPER_KEY, null);
			if (mondrianUserRoleMapper != null) {
				String url = datasource.getProperties().getProperty(ISaikuConnection.URL_KEY);
				url = url.replaceAll(";","\n");
				StringReader sr = new StringReader(url);
				Properties props = new Properties();
				props.load(sr);

				//				String catalog = props.getProperty(RolapConnectionProperties.Catalog.name());
				OlapConnection c = (OlapConnection) con.getConnection();
				//				System.out.println("CatalogParse:" + c.getCatalog());

				String[] validMondrianRolesForUser = mondrianUserRoleMapper.mapConnectionRoles(PentahoSessionHolder.getSession(), c.getCatalog());
				if (setRole(con, validMondrianRolesForUser, datasource)) {
					return con;
				}
			}
		}

		return con;

	}

	private boolean setRole(ISaikuConnection con, String[] validMondrianRolesForUser, SaikuDatasource datasource) {
		if (con.getConnection() instanceof OlapConnection) 
		{
			OlapConnection c = (OlapConnection) con.getConnection();
			System.out.println("Setting role to datasource:" + datasource.getName() + " role:" + validMondrianRolesForUser);
			try {
				SaikuMondrianHelper.setRoles(c, validMondrianRolesForUser);
				return true;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	private ISaikuConnection connect(String name, SaikuDatasource datasource) {
		try {
			ISaikuConnection con = SaikuConnectionFactory.getConnection(datasource);
			if (con.initialized()) {
				return con;
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

}
