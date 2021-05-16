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

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

public class JdbcUserPersistence implements UserPersistence {

    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss.SSS");
    private Connection connection;

    protected Connection connect() {
        try {
            if (connection == null || !connection.isValid(1)) {
                connection = DriverManager.getConnection("jdbc:h2:~/tikware", "sa", "");
            }
            return connection;
        } catch (SQLException throwable) {
            throw new Error("Failed connecting database. " + throwable.getMessage(), throwable);
        }
    }

    @Override
    public String getTradingDay() {
        ensureTradingDay();
        try (Statement stmt = connect().createStatement()) {
            var rs = stmt.executeQuery("SELECT _TRADING_DAY FROM _TRADING_DAY_TABLE ORDER BY _TIME DESC LIMIT 1");
            if (rs.next()) {
                return rs.getString("_TRADING_DAY");
            } else {
                return "";
            }
        } catch (SQLException error) {
            throw new Error("Failed query trading day. " + error.getMessage(), error);
        }
    }

    @Override
    public String getDateTime() {
        return LocalDateTime.now().format(fmt);
    }

    @Override
    public Double getPrice(String symbol) {
        ensurePrice();
        try (PreparedStatement stmt = connect().prepareStatement(
                "SELECT _PRICE FROM _PRICE_TABLE WHERE _SYMBOL=? ORDER BY _TIME DESC LIMIT 1")) {
            stmt.setString(1, symbol);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("_PRICE");
            } else {
                return Double.NaN;
            }
        } catch (SQLException error) {
            throw new Error("Failed query price for " + symbol +". " + error.getMessage(), error);
        }
    }

    @Override
    public Long getMultiple(String symbol) {
        return null;
    }

    @Override
    public Double getMargin(String symbol, Double price, Character direction, Character offset) {
        return null;
    }

    @Override
    public Double getCommission(String symbol, Double price, Character direction, Character offset) {
        return null;
    }

    protected void ensureTradingDay() {
        try {
            if (tableExists("%", "_TRADING_DAY_TABLE")) {
                return;
            }
            createTable("CREATE TABLE _TRADING_DAY_TABLE (_TIME CHAR(32), _TRADING_DAY CHAR(8))");
        } catch (SQLException throwable) {
            throw new Error("Failed ensuring trading day table. " + throwable.getMessage(), throwable);
        }
    }

    protected void ensurePrice() {
        try {
            if (tableExists("%", "_PRICE_TABLE")) {
                return;
            }
            createTable("CREATE TABLE _PRICE_TABLE (_TIME CHAR(32), _SYMBOL CHAR(128), _PRICE DOUBLE)");
        } catch (SQLException throwable) {
            throw new Error("Failed ensuring price table." + throwable.getMessage(), throwable);
        }
    }

    private boolean tableExists(String schema, String table) throws SQLException {
        var meta = connect().getMetaData();
        var t = meta.getTables(null, schema, table, new String[]{"TABLE"});
        return t.next();
    }

    private void createTable(String sql) throws SQLException {
        try (Statement stmt = connect().createStatement()) {
            stmt.execute(sql);
        }
    }

    @Override
    public void addTradingDay(String tradingDay) {
        ensureTradingDay();
        try (PreparedStatement stmt = connect().prepareStatement(
                "INSERT INTO _TRADING_DAY_TABLE(_TIME, _TRADING_DAY) VALUES (?, ?)")) {
            stmt.setString(1, getDateTime());
            stmt.setString(2, tradingDay);
            stmt.execute();
            var c = stmt.getUpdateCount();
            if (c != 1) {
                throw new Error("Failed adding trading day, affected " + c + " rows.");
            }
        } catch (SQLException error) {
            throw new Error("Failed adding or updating trading day. " + error.getMessage(), error);
        }
    }

    @Override
    public void addOrUpdatePrice(String symbol, Double price) {
        ensurePrice();
        var p = getPrice(symbol);
        if (p.isNaN()) {
            addPrice(symbol, price);
        } else {
            updatePrice(symbol, price);
        }
    }

    private void addPrice(String symbol, Double price) {
        try (PreparedStatement stmt = connect().prepareStatement(
                "INSERT INTO _PRICE_TABLE (_TIME, _SYMBOL, _PRICE) VALUES (?,?,?)")) {
            stmt.setString(1, getDateTime());
            stmt.setString(2, symbol);
            stmt.setDouble(3, price);
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed adding price for " + symbol);
            }
        } catch (SQLException error) {
            throw new Error("Failed adding price for " + symbol +". "
                            + error.getMessage(), error);
        }
    }

    private void updatePrice(String symbol, Double price) {
        try (PreparedStatement stmt = connect().prepareStatement(
                "UPDATE _PRICE_TABLE SET _PRICE = ? WHERE _SYMBOL = ?")){
            stmt.setDouble(1, price);
            stmt.setString(2, symbol);
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed updating price for " + symbol);
            }
        } catch (SQLException error) {
            throw new Error("Failed updating price for " + symbol +". "
                            + error.getMessage(), error);
        }
    }

    @Override
    public void addOrUpdateMultiple(String symbol, Long multiple) {

    }

    @Override
    public void addOrUpdateMarginRatio(String symbol, Double ratio, Character direction, Character offset, Character type) {

    }

    @Override
    public void addOrUpdateCommissionRatio(String symbol, Double ratio, Character direction, Character offset, Character type) {

    }

    @Override
    public UserBalance getUserBalance(String user) {
        return null;
    }

    @Override
    public void alterUserBalance(String user, UserBalance balance, Character alter) {

    }

    @Override
    public Collection<UserPosition> getUserPositions(String user) {
        return null;
    }

    @Override
    public void alterUserPosition(String user, UserPosition position, Character alter) {

    }

    @Override
    public Collection<UserCash> getUserCashes(String user) {
        return null;
    }

    @Override
    public void alterUserCash(String user, UserCash cash, Character alter) {

    }

    @Override
    public Collection<UserCommission> getUserCommissions(String user) {
        return null;
    }

    @Override
    public void alterUserCommission(String user, UserCommission commission, Character alter) {

    }

    @Override
    public Collection<UserInfo> getUserInfos() {
        return null;
    }

    @Override
    public void alterUserInfos(UserInfo user, Character alter) {

    }
}
