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

import org.tikware.api.Order;
import org.tikware.api.OrderListener;
import org.tikware.api.Trade;
import org.tikware.user.CloseInfo;
import org.tikware.user.QuoteInfo;
import org.tikware.user.User;

import java.util.List;

public class CloseQuoteListener extends QuoteListener {
    private final OrderListener child;

    public CloseQuoteListener(User user, OrderListener child, List<CloseInfo> infos) {
        super(user, child, infos);
        this.child = child;
    }

    @Override
    protected void process(QuoteInfo info, Trade trade, User user) {
        var offset = trade.getOffset();
        if (offset == Order.CLOSE) {
            user.close(user.getBalance().getUser(), info.getPositionId(),
                    info.getCommissionId(), trade.getPrice());
            child.onTrade(trade);
        } else {
            var u = user.getBalance().getUser();
            onError(new IllegalOffsetError(trade.getId() + "/" + u + "/" + offset.toString()));
        }
    }

    @Override
    protected void process(Throwable error) {
        // Undo rest infos.
        infos().forEach(info -> user().undo((CloseInfo) info));
    }
}
