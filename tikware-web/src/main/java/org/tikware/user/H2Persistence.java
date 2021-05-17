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

import java.io.IOException;
import java.nio.file.Files;
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

    public Path getPath() {
        return Paths.get(dir, db);
    }

    public void clearDb() {
        try {
            if (Files.exists(getPath())) {
                Files.delete(getPath());
            }
        } catch (IOException e) {
            throw new Error("Failed deleting database files: " + getPath() + ".", e);
        }
    }

    @Override
    public Connection connect() {
        try {
            ensurePath(dir);
            return DriverManager.getConnection("jdbc:h2:" + concat(dir, db), "sa", "");
        } catch (SQLException throwable) {
            throw new Error("Failed connecting database. " + throwable.getMessage(), throwable);
        }
    }

    private void ensurePath(String path) {
        Path p = Paths.get(path);
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new Error("Failed creating directories " + path + ".", e);
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
