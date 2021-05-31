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

package org.tikware.bot.mem;

import org.tikware.user.JdbcUserPersistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class InMemoryPersistence extends JdbcUserPersistence {
    private Connection c;

    @Override
    public synchronized Connection open() {
        if (c == null) {
            try {
                c = DriverManager.getConnection("jdbc:h2:mem:unittest", "sa", "");
            } catch (SQLException error) {
                throw new Error(error.getMessage(), error);
            }
        }
        return c;
    }
}
