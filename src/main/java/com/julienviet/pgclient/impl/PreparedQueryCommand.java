package com.julienviet.pgclient.impl;

import com.julienviet.pgclient.codec.Message;
import com.julienviet.pgclient.codec.decoder.message.BindComplete;
import com.julienviet.pgclient.codec.decoder.message.NoData;
import com.julienviet.pgclient.codec.decoder.message.ParameterDescription;
import com.julienviet.pgclient.codec.decoder.message.ParseComplete;
import com.julienviet.pgclient.codec.decoder.message.ReadyForQuery;
import com.julienviet.pgclient.codec.encoder.message.Bind;
import com.julienviet.pgclient.codec.encoder.message.Describe;
import com.julienviet.pgclient.codec.encoder.message.Execute;
import com.julienviet.pgclient.codec.encoder.message.Parse;
import com.julienviet.pgclient.codec.encoder.message.Sync;
import com.julienviet.pgclient.codec.util.Util;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class PreparedQueryCommand extends QueryCommandBase {


  final PreparedStatementImpl ps;
  final List<List<Object>> paramsList;
  final Handler<AsyncResult<List<ResultSet>>> handler;
  private ArrayList<ResultSet> results;
  private ResultSet result;

  PreparedQueryCommand(PreparedStatementImpl ps, List<List<Object>> paramsList, Handler<AsyncResult<List<ResultSet>>> handler) {
    this.ps = ps;
    this.paramsList = paramsList;
    this.handler = handler;
    this.results = new ArrayList<>(paramsList.size()); // Should reuse the paramsList for this as it's already allocated
  }

  @Override
  void handleDescription(List<String> columnNames) {
    result = new ResultSet().setColumnNames(columnNames).setResults(new ArrayList<>());
  }

  @Override
  void handleRow(JsonArray row) {
    result.getResults().add(row);
  }

  @Override
  boolean exec(DbConnection conn) {
    if (!ps.parsed) {
      ps.parsed = true;
      conn.writeToChannel(new Parse(ps.sql).setStatement(ps.stmt));
    }
    for (List<Object> params : paramsList) {
      conn.writeToChannel(new Bind().setParamValues(Util.paramValues(params)).setStatement(ps.stmt));
      conn.writeToChannel(new Describe().setStatement(ps.stmt));
      conn.writeToChannel(new Execute().setRowCount(0));
    }
    conn.writeToChannel(Sync.INSTANCE);
    return true;
  }

  @Override
  public boolean handleMessage(Message msg) {
    if (msg.getClass() == ReadyForQuery.class) {
      handler.handle(Future.succeededFuture(results));
      return true;
    } else if (msg.getClass() == ParameterDescription.class) {
      return false;
    } else if (msg.getClass() == NoData.class) {
      return false;
    } else if (msg.getClass() == ParseComplete.class) {
      return false;
    } else if (msg.getClass() == BindComplete.class) {
      return false;
    } else {
      return super.handleMessage(msg);
    }
  }

  @Override
  void handleComplete() {
    results.add(result);
    result = null;
  }

  @Override
  void fail(Throwable cause) {
    throw new UnsupportedOperationException("Not yet implemented");
  }
}