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

import org.tikware.api.CandleListener;
import org.tikware.api.TickListener;
import org.tikware.spi.Datafeed;

public class InMemoryDatafeed implements Datafeed {
    @Override
    public void subscribe(String symbol, TickListener listener) {
        throw new UnsupportedOperationException("subscribe");
    }

    @Override
    public void subscribe(String symbol, CandleListener listener) {
        throw new UnsupportedOperationException("subscribe");
    }

    @Override
    public Character getTradingState(String symbol) {
        throw new UnsupportedOperationException("getTradingState");
    }
}
