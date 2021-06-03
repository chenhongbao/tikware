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

package org.tikware.spi;

import org.tikware.api.CandleListener;
import org.tikware.api.TickListener;

public interface Datafeed {

    Character CONTINUOUS = 'Y';
    Character NO_TRADING = 'Z';
    Character CLOSED = 'a';

    /**
     * Subscribe for tick of the specified instrument.
     * @param symbol symbol
     * @param listener tick callback
     */
    void subscribe(String symbol, TickListener listener);

    /**
     * Subscribe for one-minute candle of the specified instrument.
     * @param symbol symbol
     * @param listener candle callback
     */
    void subscribe(String symbol, CandleListener listener);

    /**
     * Get current trading state of the specified symbol.
     * @param symbol symbol
     * @return trading state
     */
    Character getTradingState(String symbol);
}
