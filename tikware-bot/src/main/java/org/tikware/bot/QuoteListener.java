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

import org.tikware.api.OrderListener;
import org.tikware.api.Trade;
import org.tikware.user.QuoteInfo;
import org.tikware.user.User;

import java.util.LinkedList;
import java.util.List;

public abstract class QuoteListener implements OrderListener {
    private final User user;
    private final OrderListener child;
    private final List<QuoteInfo> infos = new LinkedList<>();

    public QuoteListener(User user, OrderListener child, List<? extends QuoteInfo> infos) {
        this.user = user;
        this.child  = child;
        this.infos.addAll(infos);
    }

    @Override
    public void onTrade(Trade trade) {
        if (infos.size() < trade.getQuantity()) {
            onError(new QuoteInfoUnderflowError(
                    "Information needs " + trade.getQuantity() + " elements at least."));
            return;
        }
        var count = 0;
        while (count++ < trade.getQuantity()) {
            var info = infos.remove(0);
            try {
                process(info, trade, user);
            } catch (Throwable error) {
                onError(error);
            }
        }
    }

    protected abstract void process(QuoteInfo info, Trade trade, User user);

    @Override
    public void onError(Throwable error) {
        try {
            child.onError(error);
        } catch (Throwable error2) {
            try {
                child.onError(error2);
            } catch (Throwable ignored) {
            }
        }
    }
}
