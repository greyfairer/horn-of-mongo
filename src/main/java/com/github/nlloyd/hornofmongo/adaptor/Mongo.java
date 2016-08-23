/**
 *  Copyright (c) 2013 Nick Lloyd
 *  
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *  
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *  
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.github.nlloyd.hornofmongo.adaptor;

import com.github.nlloyd.hornofmongo.MongoRuntime;
import com.github.nlloyd.hornofmongo.MongoScope;
import com.github.nlloyd.hornofmongo.action.NewInstanceAction;
import com.github.nlloyd.hornofmongo.bson.HornOfMongoBSONDecoder;
import com.github.nlloyd.hornofmongo.bson.HornOfMongoBSONEncoder;
import com.github.nlloyd.hornofmongo.util.BSONizer;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoException;
import com.mongodb.MongoOptions;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.annotations.JSConstructor;
import org.mozilla.javascript.annotations.JSFunction;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * JavaScript host Mongo object that acts as an adaptor between the JavaScript
 * Mongo API and the {@link com.mongodb.Mongo} Java driver class.
 * 
 * @author nlloyd
 * 
 */
public class Mongo extends ScriptableMongoObject {

    /**
	 * 
	 */
    private static final long serialVersionUID = 6810309240609504412L;

    /**
     * Copy of a private static final variable from {@link MongoClientURI}
     */
    public static final String MONGO_CLIENT_URI_PREFIX = "mongodb://";

    protected com.mongodb.Mongo innerMongo;

    protected List<ServerAddress> hosts;
	protected MongoOptions mongoOptions;
	protected int options;

    public Mongo() throws UnknownHostException {
        super();
    }

    @SuppressWarnings("unchecked")
    @JSConstructor
    public Mongo(final Object host) throws UnknownHostException {
        super();
        if (host instanceof Undefined)
            this.hosts = Collections.singletonList(new ServerAddress(
                    "localhost", ServerAddress.defaultPort()));
        else if (host instanceof com.mongodb.Mongo) {
            this.innerMongo = (com.mongodb.Mongo) host;
            this.hosts = this.innerMongo.getAllAddress();
            this.mongoOptions = this.innerMongo.getMongoOptions();
            // now get the query options, not same as MongoOptions
            this.options = this.innerMongo.getOptions();
        } else if (host instanceof List<?>)
            // TODO check if we get a list of ServerAddresses or something else
            this.hosts = (List<ServerAddress>) host;
        else {
            String hostsString = Context.toString(host);
            if (hostsString.startsWith(MONGO_CLIENT_URI_PREFIX))
                hostsString = hostsString.substring(MONGO_CLIENT_URI_PREFIX
                        .length());
            String[] hostStrings = hostsString.split(",");
            this.hosts = new ArrayList<ServerAddress>(hostStrings.length);
            for (String hostString : hostStrings) {
                if (hostString.indexOf(':') > -1) {
                    String[] hostBits = hostString.split(":");
                    this.hosts.add(new ServerAddress(hostBits[0], Integer
                            .valueOf(hostBits[1])));
                } else
                    this.hosts.add(new ServerAddress(hostString, ServerAddress
                            .defaultPort()));
            }
        }

        StringBuilder hostStringBuilder = new StringBuilder();
        if (!(host instanceof Undefined)) {
            for (ServerAddress serverAddress : this.hosts) {
                if (hostStringBuilder.length() > 0)
                    hostStringBuilder.append(",");
                hostStringBuilder.append(serverAddress.getHost()).append(":")
                        .append(serverAddress.getPort());
            }
        } else
            hostStringBuilder.append("127.0.0.1");
        put("host", this, hostStringBuilder.toString());
    }

    private void initMongoConnection() throws UnknownHostException {
        if ((innerMongo == null)) {
	        MongoClientOptions.Builder builder = MongoClientOptions.builder();
	        if (mongoOptions != null) {
		        //Restore previous options
		        builder.description(mongoOptions.description);
		        builder.connectionsPerHost(mongoOptions.connectionsPerHost);
		        builder.threadsAllowedToBlockForConnectionMultiplier(mongoOptions.threadsAllowedToBlockForConnectionMultiplier);
		        builder.maxWaitTime(mongoOptions.maxWaitTime);
		        builder.connectTimeout(mongoOptions.connectTimeout);
		        builder.socketTimeout(mongoOptions.socketTimeout);
		        builder.socketKeepAlive(mongoOptions.socketKeepAlive);
	        }
	        MongoClientOptions clientOptions = builder
			        .dbEncoderFactory(HornOfMongoBSONEncoder.FACTORY).build();
	        this.innerMongo = new com.mongodb.MongoClient(this.hosts,
                    clientOptions);
	        if(options != 0)
	            this.innerMongo.setOptions(options);
        }
        if (mongoScope.useMongoShellWriteConcern())
            innerMongo.setWriteConcern(WriteConcern.UNACKNOWLEDGED);
    }

    public void close() {
        if (innerMongo != null)
            innerMongo.close();
    }

    /**
     * Extracts the useMongoShellWriteConcern flag from the owning
     * {@link MongoScope} when the parent heirarchy is set.
     * 
     * @see org.mozilla.javascript.ScriptableObject#setParentScope(org.mozilla.javascript.Scriptable)
     */
    @Override
    public void setParentScope(Scriptable m) {
        super.setParentScope(m);
        // don't create a client connection for the prototype instance
        if (ScriptableObject.getClassPrototype(m, getClassName()) != null) {
            try {
                initMongoConnection();
                mongoScope.addMongoConnection(this);
            } catch (UnknownHostException e) {
                Context.throwAsScriptRuntimeEx(e);
            }
        }
    }

    public com.mongodb.Mongo getInnerMongo() {
        return innerMongo;
    }

    /**
     * @see org.mozilla.javascript.ScriptableObject#getClassName()
     */
    @Override
    public String getClassName() {
        return this.getClass().getSimpleName();
    }

    // --- Mongo JavaScript function implementation ---

    @JSFunction
    public Object find(final String ns, final Object query,
            final Object fields, Integer limit, Integer skip,
            Integer batchSize, Integer options) {
        Object result = null;

        Object rawQuery = BSONizer.convertJStoBSON(query, false);
        Object rawFields = BSONizer.convertJStoBSON(fields, false);
        DBObject bsonQuery = null;
        DBObject bsonFields = null;
        if (rawQuery instanceof DBObject)
            bsonQuery = (DBObject) rawQuery;
        if (rawFields instanceof DBObject)
            bsonFields = (DBObject) rawFields;
        com.mongodb.DB db = innerMongo.getDB(ns.substring(0, ns.indexOf('.')));
        String collectionName = ns.substring(ns.indexOf('.') + 1);
        if ("$cmd".equals(collectionName)) {
            try {
                if(options == 0)
                    options = innerMongo.getOptions();
//GC: 16/11/15 fixed for v3
//                CommandResult cmdResult = db.command(bsonQuery, options,
                CommandResult cmdResult = db.command(bsonQuery, innerMongo.getReadPreference(),
                        HornOfMongoBSONEncoder.FACTORY.create());
//GC: 16/11/15 removed for v3
//                handlePostCommandActions(db, bsonQuery);
                Object jsCmdResult = BSONizer.convertBSONtoJS(mongoScope,
                        cmdResult);
                result = MongoRuntime.call(new NewInstanceAction(mongoScope,
                        "InternalCursor", new Object[] { jsCmdResult }));
            } catch (NoSuchElementException nse) {
                // thrown when db.runCommand() called (no arguments)
                CommandResult failedCmdResult = db.command(this.hosts
                        .iterator().next().toString());
                failedCmdResult.put("ok", Boolean.FALSE);
                failedCmdResult.put("errmsg", "no such cmd: ");
                Object jsFailedCmdResult = BSONizer.convertBSONtoJS(mongoScope,
                        failedCmdResult);
                result = MongoRuntime.call(new NewInstanceAction(mongoScope,
                        "InternalCursor", new Object[] { jsFailedCmdResult }));
            } catch (MongoException me) {
                handleMongoException(me);
            }
        } else {
            DBCollection collection = db.getCollection(collectionName);
            collection.setDBEncoderFactory(HornOfMongoBSONEncoder.FACTORY);
            collection.setDBDecoderFactory(HornOfMongoBSONDecoder.FACTORY);
            DBObject specialFields = null;
            if(bsonQuery.get("query") instanceof DBObject) {
                specialFields = bsonQuery;
                bsonQuery = (DBObject)bsonQuery.get("query");
            }
            DBCursor cursor = collection.find(bsonQuery, bsonFields).skip(skip)
                    .batchSize(batchSize).limit(limit).addOption(options);
            if(specialFields != null) {
                for(String key : specialFields.keySet()) {
                    if(!"query".equals(key))
                        cursor.addSpecial(key, specialFields.get(key));
                }
            }

            InternalCursor jsCursor = (InternalCursor) MongoRuntime
                    .call(new NewInstanceAction(mongoScope, "InternalCursor",
                            new Object[] { cursor }));
            result = jsCursor;
        }

        return result;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @JSFunction
    public void insert(final String ns, Object obj, int options) {
        Object rawObj = BSONizer.convertJStoBSON(obj, true);
        DBObject bsonObj = null;
        if (rawObj instanceof DBObject)
            bsonObj = (DBObject) rawObj;

        try {
            int dbSeparatorIdx = ns.indexOf('.');
            com.mongodb.DB db = innerMongo.getDB(ns
                    .substring(0, dbSeparatorIdx));
            String collectionName = ns.substring(dbSeparatorIdx + 1);
            DBCollection collection = db.getCollection(collectionName);
            collection.setDBEncoderFactory(HornOfMongoBSONEncoder.FACTORY);
            collection.setDBDecoderFactory(HornOfMongoBSONDecoder.FACTORY);
            // unfortunately the Java driver does not expose the _allow_dot
            // argument in insert calls so we need to translate system.indexes
            // inserts into index creation calls through the java driver
            if (collectionName.endsWith("system.indexes")) {
                  db.getCollection("system.indexes").insert(Arrays.asList(bsonObj));
            } else {
                int oldOptions = collection.getOptions();
                collection.setOptions(options);

                List insertObj = null;
                if (rawObj instanceof List)
                    insertObj = (List) rawObj;
                else
                    insertObj = Arrays.asList(rawObj);
                collection.insert(insertObj);
                collection.setOptions(oldOptions);
            }
            saveLastCalledDB(db);
        } catch (MongoException me) {
            handleMongoException(me);
        }
    }

    @JSFunction
    public void remove(final String ns, Object pattern, boolean justOne) {
        Object rawPattern = BSONizer.convertJStoBSON(pattern, false);
        DBObject bsonPattern = null;
        if (rawPattern instanceof DBObject)
            bsonPattern = (DBObject) rawPattern;

        com.mongodb.DB db = innerMongo.getDB(ns.substring(0, ns.indexOf('.')));
        DBCollection collection = db
                .getCollection(ns.substring(ns.indexOf('.') + 1));
        collection.setDBEncoderFactory(HornOfMongoBSONEncoder.FACTORY);

        try {
            collection.remove(bsonPattern);
            saveLastCalledDB(db);
        } catch (MongoException me) {
            handleMongoException(me);
        }
    }

    @JSFunction
    public void update(final String ns, Object query, Object obj,
            final Boolean upsert, final Boolean multi) {
        Object rawQuery = BSONizer.convertJStoBSON(query, false);
        Object rawObj = BSONizer.convertJStoBSON(obj, true);
        DBObject bsonQuery = null;
        DBObject bsonObj = null;
        if (rawQuery instanceof DBObject)
            bsonQuery = (DBObject) rawQuery;
        if (rawObj instanceof DBObject)
            bsonObj = (DBObject) rawObj;

        boolean upsertOp = (upsert != null) ? upsert : false;
        boolean multiOp = (multi != null) ? multi : false;

        com.mongodb.DB db = innerMongo.getDB(ns.substring(0, ns.indexOf('.')));
        DBCollection collection = db
                .getCollection(ns.substring(ns.indexOf('.') + 1));
        collection.setDBEncoderFactory(HornOfMongoBSONEncoder.FACTORY);

        try {
            collection.update(bsonQuery, bsonObj, upsertOp, multiOp);
            saveLastCalledDB(db);
        } catch (MongoException me) {
            handleMongoException(me);
        }
    }

    /**
     * Run the { logout: 1 } command against the db with the given name.
     * 
     * @param dbName
     * @return
     */
    @JSFunction
    public Object logout(final String dbName) {
        DB db = innerMongo.getDB(dbName);
        CommandResult result = db.command(new BasicDBObject("logout", 1), innerMongo.getReadPreference());
        return BSONizer.convertBSONtoJS(mongoScope, result);
    }

    private void handleMongoException(MongoException me) {
        if (mongoScope == null)
            mongoScope = (MongoScope) ScriptableObject.getTopLevelScope(this);
        mongoScope.handleMongoException(me);
    }

    private void saveLastCalledDB(com.mongodb.DB lastCalledDB) {
        if(mongoScope == null)
            mongoScope = (MongoScope)ScriptableObject.getTopLevelScope(this);
        mongoScope.setLastCalledDB(lastCalledDB);
    }
    
}
