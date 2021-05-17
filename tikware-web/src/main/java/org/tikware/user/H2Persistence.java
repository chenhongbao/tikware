/*
 * Copyright (c) 2020-2021. Hongbao Chen <chenhongbao@outlook.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.tikware.user;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class H2Persistence extends JdbcUserPersistence {
    private final String dir;
    private final String db;

    public H2Persistence(String directory, String db) {
        this.dir = directory;
        this.db = db;
    }

    public void deleteDb() {
        close();
        org.h2.tools.DeleteDbFiles.execute(dir, db, true);
    }

    public void close() {
        try {
            if (connection().isClosed()) {
                return;
            }
            connection().close();
        } catch (SQLException error) {
            throw new Error("Failed closing connection: " + concat(dir, db) + ". "
                            + error.getMessage(), error);
        }
    }

    private Path getActualPath(String dir) {
        var i = dir.lastIndexOf('~');
        if (i == -1) {
            return Paths.get(dir).toAbsolutePath();
        } else {
            return Paths.get(System.getProperty( "user.dir"), dir.substring(i+1))
                        .toAbsolutePath();
        }
    }

    @Override
    public Connection open() {
        try {
            return DriverManager.getConnection("jdbc:h2:" + concat(dir, db), "sa", "");
        } catch (SQLException throwable) {
            throw new Error("Failed connecting database. " + throwable.getMessage(), throwable);
        }
    }

    private String concat(String path, String db) {
        if (!path.endsWith("/")) {
            return path + "/" + db;
        } else {
            return path + db;
        }
    }
}
