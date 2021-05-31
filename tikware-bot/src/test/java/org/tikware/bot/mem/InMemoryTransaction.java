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

import org.tikware.api.Order;
import org.tikware.api.OrderListener;
import org.tikware.api.Trade;
import org.tikware.spi.Transaction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InMemoryTransaction implements Transaction {

    private final Map<Order, OrderInfo> callbacks;

    public InMemoryTransaction() {
        callbacks = new HashMap<>();
    }

    @Override
    public void quote(Order order, OrderListener listener) {
        if (callbacks.containsKey(order)) {
            listener.onError(new Error("Order has been submitted."));
        } else {
            callbacks.put(order, new OrderInfo(order, listener));
        }
    }

    @Override
    public String getTradingDay() {
        return null;
    }

    public void fill(Order order, int quantity, double price) {
        updateQuantity(order, quantity);
        listener(order).onTrade(extractTrade(order, quantity, price));
    }

    private void updateQuantity(Order order, int quantity) {
        var o = callbacks.get(order);
        if (o == null) {
            throw new Error("Unknown order: " + order.getId() + ".");
        }
        if (quantity > o.totalQuantity) {
            throw new Error("Insufficient position: " + o.totalQuantity +".");
        } else {
            o.totalQuantity -= quantity;
        }
    }

    private Trade extractTrade(Order order, int quantity, double price) {
        var t = new Trade();
        t.setId("T" + UUID.randomUUID().getLeastSignificantBits());
        t.setUser(order.getUser());
        t.setDirection(order.getDirection());
        t.setOffset(order.getOffset());
        t.setOrderId(order.getId());
        t.setTradingDay("20210531");
        t.setTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss SSS")));
        t.setQuantity((long) quantity);
        t.setPrice(price);
        t.setSymbol(order.getSymbol());
        t.setExchange(order.getExchange());
        return t;
    }

    private OrderListener listener(Order order) {
        var x = callbacks.get(order);
        if (x == null) {
            throw new Error("Unknown order: " + order.getId() + ".");
        }
        return x.listener;
    }

    private class OrderInfo {
        final OrderListener listener;
        final Order order;
        private long totalQuantity;

        OrderInfo(Order order, OrderListener listener) {
            this.order = order;
            this.listener = listener;
            this.totalQuantity =  order.getQuantity();
        }
    }
}
