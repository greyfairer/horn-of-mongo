/**
 *  Copyright (c) 2012 Nick Lloyd
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
package org.github.nlloyd.hornofmongo;

import java.net.UnknownHostException;

import org.apache.commons.lang3.StringUtils;
import org.github.nlloyd.hornofmongo.action.MongoAction;
import org.github.nlloyd.hornofmongo.action.NewInstanceAction;
import org.github.nlloyd.hornofmongo.bson.HornOfMongoBSONEncoder;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ScriptableObject;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;

/**
 * Runtime class for the Horn of Mongo client library that initializes the
 * global {@link ContextFactory} and provides convenience methods for executing
 * JS scripts with mongodb JSAPI code.
 * 
 * @author nlloyd
 * 
 */
public class MongoRuntime {

    /**
     * Creates a newly initialized {@link MongoScope} instance. This will use
     * {@link MongoRuntime#call(MongoAction)} to initialize the
     * {@link MongoScope} instance, possibly resulting in the global
     * {@link MongoContextFactory} being set.
     * 
     * @return
     */
    public static final MongoScope createMongoScope() {
        MongoScope mongoScope = (MongoScope) call(new MongoScope.InitMongoScopeAction());
        return mongoScope;
    }

    public static final MongoScope createMongoScope(
            final MongoClientURI mongoClientURI, boolean useMongoShellWriteConcern,
            boolean mimicShellExceptionBehavior) throws UnknownHostException {
        if (StringUtils.isBlank(mongoClientURI.getDatabase()))
            throw new IllegalArgumentException(
                    "mongo client uri must have a database");
        MongoScope mongoScope = createMongoScope();
        mongoScope.setUseMongoShellWriteConcern(useMongoShellWriteConcern);
        mongoScope.setMimicShellExceptionBehavior(mimicShellExceptionBehavior);
        
        MongoClientOptions.Builder clientOptionsBuilder = MongoClientOptions
                .builder().dbEncoderFactory(HornOfMongoBSONEncoder.FACTORY);
        MongoClientURI realClientURI = new MongoClientURI(
                mongoClientURI.getURI(), clientOptionsBuilder);
        MongoClient mongoConnection = new MongoClient(realClientURI);

        Object mongo = MongoRuntime.call(new NewInstanceAction(mongoScope,
                "Mongo", new Object[] { mongoConnection }));
        Object db = MongoRuntime.call(new NewInstanceAction(mongoScope, "DB",
                new Object[] { mongo, realClientURI.getDatabase() }));

        ScriptableObject.defineProperty(mongoScope, "db", db,
                ScriptableObject.EMPTY);

        return mongoScope;
    }

    /**
     * Convenience method to call the {@link MongoAction} using the global
     * {@link ContextFactory}. If the global {@link ContextFactory} has not
     * explicitly been set yet then this method will set an instance of
     * {@link MongoContextFactory} as the global {@link ContextFactory}.
     * 
     * @param mongoAction
     * @return
     */
    public static final Object call(MongoAction mongoAction) {
        if (!ContextFactory.hasExplicitGlobal())
            ContextFactory.initGlobal(new MongoContextFactory());
        return ContextFactory.getGlobal().call(mongoAction);
    }

}
