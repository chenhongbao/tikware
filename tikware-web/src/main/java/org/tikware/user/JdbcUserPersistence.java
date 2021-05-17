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
import java.util.Objects;

public abstract class JdbcUserPersistence implements UserPersistence {
    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss.SSS");
    private Connection dbc;

    /**
     * Provide connection to custom data source.
     * @return {@link Connection}
     */
    public abstract Connection connect();

    protected Connection connection() {
        try {
            if (dbc == null || !dbc.isValid(1)) {
                dbc = connect();
            }
            return dbc;
        } catch (SQLException error) {
            throw new Error("Failed validating database connection. " + error.getMessage(), error);
        }
    }

    @Override
    public String getTradingDay() {
        ensureTradingDay();
        try (Statement stmt = connection().createStatement()) {
            var rs = stmt.executeQuery("SELECT _TRADING_DAY FROM _TRADING_DAY_TABLE ORDER BY _TIME DESC LIMIT 1");
            if (rs.next()) {
                var day = rs.getString("_TRADING_DAY");
                rs.close();
                return day;
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
        try (PreparedStatement stmt = connection().prepareStatement(
                "SELECT _PRICE FROM _PRICE_TABLE WHERE _SYMBOL=? ORDER BY _TIME DESC LIMIT 1")) {
            stmt.setString(1, symbol);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                var p = rs.getDouble("_PRICE");
                rs.close();
                return p;
            } else {
                return Double.NaN;
            }
        } catch (SQLException error) {
            throw new Error("Failed query price for " + symbol + ". " + error.getMessage(), error);
        }
    }

    @Override
    public Long getMultiple(String symbol) {
        ensureMultiple();
        try (PreparedStatement stmt = connection().prepareStatement(
                "SELECT _MULTIPLE FROM _MULTIPLE_TABLE WHERE _SYMBOL = ? ORDER BY _TIME DESC LIMIT 1")) {
            stmt.setString(1, symbol);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                var m = (long) rs.getInt("_MULTIPLE");
                rs.close();
                return m;
            } else {
                return null;
            }
        } catch (SQLException error) {
            throw new Error("Failed query multiple for " + symbol + ". " + error.getMessage(), error);
        }
    }

    private void ensureMultiple() {
        try {
            if (tableExists("%", "_MULTIPLE_TABLE")) {
                return;
            }
            createTable("CREATE TABLE _MULTIPLE_TABLE (_TIME CHAR(32), _SYMBOL CHAR(128), _MULTIPLE INT)");
        } catch (SQLException error) {
            throw new Error("Failed ensuring multiple table. " + error.getMessage(), error);
        }

    }

    @Override
    public Double getMargin(String symbol, Double price, Character direction, Character offset) {
        ensureMargin();
        var rs = getMarginRatio(symbol, direction, offset);
        if (rs == null) {
            return Double.NaN;
        } else {
            try {
                var type = rs.getString("_TYPE").charAt(0);
                var ratio = rs.getDouble("_RATIO");
                var margin = calcRatioFee(symbol, price, ratio, type);
                rs.close();
                return margin;
            } catch (SQLException error) {
                throw new Error("Failed calculating margin for " + symbol + ". " + error.getMessage(), error);
            }
        }
    }

    private ResultSet getMarginRatio(String symbol, Character direction, Character offset) {
        try {
            PreparedStatement stmt = connection().prepareStatement(
                    "SELECT _RATIO, _TYPE FROM _MARGIN_TABLE WHERE _SYMBOL = ? AND _DIRECTION = ?" +
                    " AND _OFFSET = ?");
            stmt.setString(1, symbol);
            stmt.setString(2, String.valueOf(direction));
            stmt.setString(3, String.valueOf(offset));
            var rs = stmt.executeQuery();
            if (rs.next()) {
                // Need to close result set later.
                return rs;
            } else {
                return null;
            }
        } catch (SQLException error) {
            throw new Error("Failed query margin for " + symbol + ", direction: "
                            + direction + ", offset: " + offset + ". " + error.getMessage(),
                    error);
        }
    }

    private Double calcRatioFee(String symbol, Double price, double ratio, char type) {
        if (Objects.equals(type, RATIO_BY_AMOUNT)) {
            var m = getMultiple(symbol);
            if (m == null) {
                throw new MultipleNotFoundException("Multiple not found for " + symbol + ".");
            } else {
                return price * m * ratio;
            }
        } else if (Objects.equals(type, RATIO_BY_VOLUME)) {
            return ratio;
        } else {
            throw new IllegalRatioTypeException("Illegal ratio type: " + type + ".");
        }
    }

    private void ensureMargin() {
        try {
            if (tableExists("%", "_MARGIN_TABLE")) {
                return;
            }
            createTable("CREATE TABLE _MARGIN_TABLE (_TIME CHAR(32), _SYMBOL CHAR(128), " +
                        "_RATIO DOUBLE, _DIRECTION CHAR(1), _OFFSET CHAR(1), _TYPE CHAR(1))");
        } catch (SQLException error) {
            throw new Error("Failed ensuring margin table. " + error.getMessage(), error);
        }

    }

    @Override
    public Double getCommission(String symbol, Double price, Character direction, Character offset) {
        ensureCommission();
        var rs = getCommissionRatio(symbol, direction, offset);
        if (rs == null) {
            return Double.NaN;
        } else {
            try {
                var type = rs.getString("_TYPE").charAt(0);
                var ratio = rs.getDouble("_RATIO");
                var commission = calcRatioFee(symbol, price, ratio, type);
                rs.close();
                return commission;
            } catch (SQLException error) {
                throw new Error("Failed calculating margin for " + symbol + ". " + error.getMessage(), error);
            }
        }
    }

    private ResultSet getCommissionRatio(String symbol, Character direction, Character offset) {
        try {
            PreparedStatement stmt = connection().prepareStatement(
                    "SELECT _RATIO, _TYPE FROM _COMMISSION_TABLE WHERE _SYMBOL = ? AND _DIRECTION = ?" +
                    " AND _OFFSET = ?");
            stmt.setString(1, symbol);
            stmt.setString(2, String.valueOf(direction));
            stmt.setString(3, String.valueOf(offset));
            var rs = stmt.executeQuery();
            if (rs.next()) {
                // Need to close result set later.
                return rs;
            } else {
                return null;
            }
        } catch (SQLException error) {
            throw new Error("Failed query commission for " + symbol + ", direction: "
                            + direction + ", offset: " + offset + ". " + error.getMessage(),
                    error);
        }
    }

    private void ensureCommission() {
        try {
            if (tableExists("%", "_COMMISSION_TABLE")) {
                return;
            }
            createTable("CREATE TABLE _COMMISSION_TABLE (_TIME CHAR(32), _SYMBOL CHAR(128), " +
                        "_RATIO DOUBLE, _DIRECTION CHAR(1), _OFFSET CHAR(1), _TYPE CHAR(1))");
        } catch (SQLException error) {
            throw new Error("Failed ensuring commission table. " + error.getMessage(), error);
        }
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
        var meta = connection().getMetaData();
        var t = meta.getTables(null, schema, table, new String[]{"TABLE"});
        return t.next();
    }

    private void createTable(String sql) throws SQLException {
        try (Statement stmt = connection().createStatement()) {
            stmt.execute(sql);
        }
    }

    @Override
    public void addTradingDay(String tradingDay) {
        ensureTradingDay();
        try (PreparedStatement stmt = connection().prepareStatement(
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
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT INTO _PRICE_TABLE (_TIME, _SYMBOL, _PRICE) VALUES (?,?,?)")) {
            stmt.setString(1, getDateTime());
            stmt.setString(2, symbol);
            stmt.setDouble(3, price);
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed adding price for " + symbol);
            }
        } catch (SQLException error) {
            throw new Error("Failed adding price for " + symbol + ". "
                            + error.getMessage(), error);
        }
    }

    private void updatePrice(String symbol, Double price) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE _PRICE_TABLE SET _PRICE = ? WHERE _SYMBOL = ?")) {
            stmt.setDouble(1, price);
            stmt.setString(2, symbol);
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed updating price for " + symbol);
            }
        } catch (SQLException error) {
            throw new Error("Failed updating price for " + symbol + ". "
                            + error.getMessage(), error);
        }
    }

    @Override
    public void addOrUpdateMultiple(String symbol, Long multiple) {
        var m = getMultiple(symbol);
        if (m == null) {
            addMultiple(symbol, multiple);
        } else {
            updateMultiple(symbol, multiple);
        }
    }

    private void addMultiple(String symbol, Long multiple) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT INTO _MULTIPLE_TABLE (_TIME, _SYMBOL, _MULTIPLE) VALUES (?,?,?)")) {
            stmt.setString(1, getDateTime());
            stmt.setString(2, symbol);
            stmt.setInt(3, multiple.intValue());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed adding multiple for " + symbol);
            }
        } catch (SQLException error) {
            throw new Error("Failed adding multiple for " + symbol + ". "
                            + error.getMessage(), error);
        }
    }

    private void updateMultiple(String symbol, Long multiple) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE _MULTIPLE_TABLE SET _MULTIPLE = ? WHERE _SYMBOL = ?")) {
            stmt.setInt(1, multiple.intValue());
            stmt.setString(2, symbol);
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed updating multiple for " + symbol);
            }
        } catch (SQLException error) {
            throw new Error("Failed updating multiple for " + symbol + ". "
                            + error.getMessage(), error);
        }
    }

    @Override
    public void addOrUpdateMarginRatio(String symbol, Double ratio, Character direction,
            Character offset, Character type) {
        var rs = getMarginRatio(symbol, direction, offset);
        if (rs == null) {
            addMarginRatio(symbol, ratio, direction, offset, type);
        } else {
            updateMarginRatio(symbol, ratio, direction, offset, type);
            try {
                rs.close();
            } catch (SQLException ignored) {
            }
        }
    }

    private void addMarginRatio(String symbol, Double ratio, Character direction,
            Character offset, Character type) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT INTO _MARGIN_TABLE (_TIME, _SYMBOL, _RATIO, _DIRECTION, _OFFSET, _TYPE) " +
                "VALUES (?,?,?,?,?,?)")) {
            stmt.setString(1, getDateTime());
            stmt.setString(2, symbol);
            stmt.setDouble(3, ratio);
            stmt.setString(4, String.valueOf(direction));
            stmt.setString(5, String.valueOf(offset));
            stmt.setString(6, String.valueOf(type));
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed adding margin ratio for " + symbol + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed adding margin ratio for " + symbol + ". " +
                            error.getMessage(), error);
        }
    }

    private void updateMarginRatio(String symbol, Double ratio, Character direction,
            Character offset, Character type) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE _MARGIN_TABLE SET _RATIO = ?, _TIME = ?, _TYPE = ? " +
                "WHERE _SYMBOL = ? AND _DIRECTION = ? AND _OFFSET = ?")) {
            stmt.setDouble(1, ratio);
            stmt.setString(2, getDateTime());
            stmt.setString(3, String.valueOf(type));
            stmt.setString(4, symbol);
            stmt.setString(5, String.valueOf(direction));
            stmt.setString(6, String.valueOf(offset));
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed updating margin ratio for " + symbol + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed updating margin ratio for " + symbol + ". " +
                            error.getMessage(), error);
        }
    }

    @Override
    public void addOrUpdateCommissionRatio(String symbol, Double ratio, Character direction,
            Character offset, Character type) {
        var rs = getCommissionRatio(symbol, direction, offset);
        if (rs == null) {
            addCommissionRatio(symbol, ratio, direction, offset, type);
        } else {
            updateCommissionRatio(symbol, ratio, direction, offset, type);
            try {
                rs.close();
            } catch (SQLException ignored) {
            }
        }
    }

    private void addCommissionRatio(String symbol, Double ratio, Character direction,
            Character offset, Character type) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT INTO _COMMISSION_TABLE (_TIME, _SYMBOL, _RATIO, _DIRECTION, _OFFSET, _TYPE) " +
                "VALUES (?,?,?,?,?,?)")) {
            stmt.setString(1, getDateTime());
            stmt.setString(2, symbol);
            stmt.setDouble(3, ratio);
            stmt.setString(4, String.valueOf(direction));
            stmt.setString(5, String.valueOf(offset));
            stmt.setString(6, String.valueOf(type));
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed adding commission ratio for " + symbol + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed adding commission ratio for " + symbol + ". " +
                            error.getMessage(), error);
        }
    }

    private void updateCommissionRatio(String symbol, Double ratio, Character direction,
            Character offset, Character type) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE _COMMISSION_TABLE SET _RATIO = ?, _TIME = ?, _TYPE = ? " +
                "WHERE _SYMBOL = ? AND _DIRECTION = ? AND _OFFSET = ?")) {
            stmt.setDouble(1, ratio);
            stmt.setString(2, getDateTime());
            stmt.setString(3, String.valueOf(type));
            stmt.setString(4, symbol);
            stmt.setString(5, String.valueOf(direction));
            stmt.setString(6, String.valueOf(offset));
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed updating commission ratio for " + symbol + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed updating commission ratio for " + symbol + ". " +
                            error.getMessage(), error);
        }
    }

    @Override
    public UserBalance getUserBalance(String user) {
        var table = ensureUserBalance(user);
        try (PreparedStatement stmt  = connection().prepareStatement(
                "SELECT * FROM " + table + " WHERE _USER = ?")) {
            stmt.setString(1, user);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return buildUserBalance(rs);
            } else {
                return null;
            }
        } catch (SQLException error) {
            throw new Error("Failed query user balance for " + user + ". " + error.getMessage(), error);
        }
    }

    private UserBalance buildUserBalance(ResultSet rs) throws SQLException {
        var b = new UserBalance();
        b.setId(rs.getString("_ID"));
        b.setUser(rs.getString("_USER"));
        b.setBalance(rs.getDouble("_BALANCE"));
        b.setTradingDay(rs.getString("_TRADING_DAY"));
        b.setTime(rs.getString("_TIME"));
        return b;
    }

    @Override
    public void alterUserBalance(String user, UserBalance balance, Character alter) {
        var b = getUserBalance(user);
        if (Objects.equals(alter, ALTER_ADD)) {
            addUserBalance(user, balance);
        } else if (Objects.equals(alter, ALTER_UPDATE)) {
            updateUserBalance(user, balance);
        } else if (Objects.equals(alter, ALTER_DELETE)) {
            deleteUserBalance(user, balance);
        } else {
            throw new Error("Unsupported data operation: " + alter + ".");
        }
    }

    private final static String USER_BALANCE_TABLE = "_USER_BALANCE_TABLE";

    private void addUserBalance(String user, UserBalance balance) {
        var table = userTableName(user, USER_BALANCE_TABLE);
        try (PreparedStatement stmt  = connection().prepareStatement(
                "INSERT INTO " + table + " (_ID, _USER, _BALANCE, _TRADING_DAY, _TIME) " +
                "VALUES (?,?,?,?,?)")) {
            stmt.setString(1, balance.getId());
            stmt.setString(2, balance.getUser());
            stmt.setDouble(3, balance.getBalance());
            stmt.setString(4, balance.getTradingDay());
            stmt.setString(5, balance.getTime());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed adding user balance for " + user + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed adding user balance for " + user + ".", error);
        }
    }

    private void updateUserBalance(String user, UserBalance balance) {
        var table = userTableName(user, USER_BALANCE_TABLE);
        try (PreparedStatement stmt  = connection().prepareStatement(
                "UPDATE " + table + " SET _BALANCE = ?,_TRADING_DAY = ?, _TIME = ? " +
                "WHERE _ID = ? AND _USER = ?")) {
            stmt.setDouble(1, balance.getBalance());
            stmt.setString(2, balance.getTradingDay());
            stmt.setString(3, balance.getTime());
            stmt.setString(4, balance.getId());
            stmt.setString(5, balance.getUser());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed updating user balance for " + user + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed updating user balance for " + user + ".", error);
        }
    }

    private void deleteUserBalance(String user, UserBalance balance) {
        var table = userTableName(user, USER_BALANCE_TABLE);
        try (PreparedStatement stmt  = connection().prepareStatement(
                "DELETE FROM " + table + " WHERE _ID = ? AND _USER = ?")) {
            stmt.setString(1, balance.getId());
            stmt.setString(2, balance.getUser());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed deleting user balance for " + user + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed deleting user balance for " + user + ".", error);
        }
    }

    private String ensureUserBalance(String user) {
        try {
            var table = userTableName(user, USER_BALANCE_TABLE);
            if (tableExists("%", table)) {
                return table;
            }
            createTable("CREATE TABLE " + table + " (_ID CHAR(128), _USER CHAR(128), " +
                        "_BALANCE DOUBLE, _TRADING_DAY CHAR(8), _TIME CHAR(32))");
            return table;
        } catch (SQLException error) {
            throw new Error("Failed ensuring user balance table. " + error.getMessage(), error);
        }
    }

    private String userTableName(String user, String table) {
        return "_" + user + table;
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
