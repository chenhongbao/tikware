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

package org.tikware.bot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tikware.bot.mem.InMemoryPersistence;
import org.tikware.user.JdbcUserPersistence;

public class BotEnvironmentTest {

    private JdbcUserPersistence p;

    @BeforeEach
    public void prepare() {
        p = new InMemoryPersistence();
    }

    @AfterEach
    public void clear() {
    }

    @Test
    public void good() {

    }

    private JdbcUserPersistence db() {
        return p;
    }
}