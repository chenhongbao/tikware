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

import java.util.Collection;

public interface UserPersistence {

    /**
     * Ratio is calculated by volume. For example, margin is calculated by
     * <pre><code>
     *     margin = ratio * volume
     * </code></pre>
     */
    Character RATIO_BY_VOLUME = 'O';

    /**
     * Ratio is calculated by amount. For example, margin is calculated by
     * <pre><code>
     *     margin = amount * ratio * volume
     * </code></pre>
     */
    Character RATIO_BY_AMOUNT = 'P';

    Character ALTER_UPDATE = 'Q';

    Character ALTER_DELETE = 'R';

    Character ALTER_ADD = 'S';

    String getTradingDay();

    String getDateTime();

    Double getPrice(String symbol);

    Long getMultiple(String symbol);

    Double getMargin(String symbol, Double price, Character direction, Character offset);

    Double getCommission(String symbol, Double price, Character direction, Character offset);

    void addTradingDay(String tradingDay);

    void addOrUpdatePrice(String symbol, Double price);

    void addOrUpdateMultiple(String symbol, Long multiple);

    void addOrUpdateMarginRatio(String symbol, Double ratio, Character direction, Character offset, Character type);

    void addOrUpdateCommissionRatio(String symbol, Double ratio, Character direction, Character offset, Character type);

    UserBalance getUserBalance(String user);

    void alterUserBalance(String user, UserBalance balance, Character alter);

    Collection<UserPosition> getUserPositions(String user);

    void alterUserPosition(String user,UserPosition position, Character alter);

    Collection<UserCash> getUserCashes(String user);

    void alterUserCash(String user, UserCash cash, Character alter);

    Collection<UserCommission> getUserCommissions(String user);

    void alterUserCommission(String user, UserCommission commission, Character alter);

    Collection<UserInfo> getUserInfos();

    void alterUserInfo(UserInfo user, Character alter);
}
