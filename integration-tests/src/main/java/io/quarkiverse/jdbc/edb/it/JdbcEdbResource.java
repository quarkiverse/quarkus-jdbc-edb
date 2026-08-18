/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package io.quarkiverse.jdbc.edb.it;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/jdbc-edb")
@Produces(MediaType.TEXT_PLAIN)
public class JdbcEdbResource {

    @Inject
    DataSource dataSource;

    @GET
    public String databaseProductName() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getDatabaseProductName();
        }
    }

    @GET
    @Path("/driver")
    public String driverName() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getDriverName();
        }
    }

    @GET
    @Path("/url")
    public String jdbcUrl() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getURL();
        }
    }

    /**
     * The implementation class of the driver's own connection. This is the unambiguous proof that
     * the EDB driver served the connection: {@code getDriverName()} is unreliable here because the
     * EDB driver is a fork of pgjdbc and may report the upstream driver name.
     * <p>
     * The unwrap is required because Agroal hands out a pooled
     * {@code io.agroal.pool.wrapper.ConnectionWrapper}; its {@code unwrap} delegates to the
     * underlying driver connection.
     */
    @GET
    @Path("/connection-class")
    public String connectionClass() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            return conn.unwrap(Connection.class).getClass().getName();
        }
    }
}
