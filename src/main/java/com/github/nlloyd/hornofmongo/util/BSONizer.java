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
package com.github.nlloyd.hornofmongo.util;

import com.github.nlloyd.hornofmongo.MongoRuntime;
import com.github.nlloyd.hornofmongo.MongoScope;
import com.github.nlloyd.hornofmongo.action.MongoAction;
import com.github.nlloyd.hornofmongo.action.NewInstanceAction;
import com.github.nlloyd.hornofmongo.adaptor.BinData;
import com.github.nlloyd.hornofmongo.adaptor.DBRef;
import com.github.nlloyd.hornofmongo.adaptor.MaxKey;
import com.github.nlloyd.hornofmongo.adaptor.MinKey;
import com.github.nlloyd.hornofmongo.adaptor.NumberInt;
import com.github.nlloyd.hornofmongo.adaptor.NumberLong;
import com.github.nlloyd.hornofmongo.adaptor.ObjectId;
import com.github.nlloyd.hornofmongo.adaptor.ScriptableMongoObject;
import com.github.nlloyd.hornofmongo.adaptor.Timestamp;
import com.github.nlloyd.hornofmongo.exception.MongoScopeException;
import com.mongodb.BasicDBObject;
import com.mongodb.Bytes;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.bson.BSON;
import org.bson.BSONObject;
import org.bson.types.BSONTimestamp;
import org.bson.types.Binary;
import org.bson.types.Code;
import org.bson.types.Symbol;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.ConsString;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;
import org.mozilla.javascript.regexp.NativeRegExp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

//GC: added 17/11/15

/**
 * @author nlloyd
 * 
 */
public class BSONizer {

    public static Object convertJStoBSON(Object jsObject, boolean isJsObj) {
        return convertJStoBSON(jsObject, isJsObj, null);
    }

    public static Object convertJStoBSON(Object jsObject, boolean isJsObj, String dateFormat) {
        Object bsonObject = null;
        if (jsObject instanceof NativeArray) {
            NativeArray jsArray = (NativeArray) jsObject;
            List<Object> bsonArray = new ArrayList<Object>(Long.valueOf(
                    jsArray.getLength()).intValue());
            for (Object jsEntry : jsArray) {
                bsonArray.add(convertJStoBSON(jsEntry, isJsObj, dateFormat));
            }
            bsonObject = bsonArray;
        } else if (jsObject instanceof NativeRegExp) {
            Object source = ScriptableObject.getProperty((Scriptable) jsObject,
                    "source");
            String fullRegex = (String) Context
                    .jsToJava(jsObject, String.class);
            String options = fullRegex
                    .substring(fullRegex.lastIndexOf("/") + 1);

            bsonObject = Pattern.compile(source.toString(),
                    Bytes.regexFlags(options));
            ;
        } else if (jsObject instanceof NativeObject) {
            BasicDBObject bson = new BasicDBObject();
            bsonObject = bson;

            NativeObject rawJsObject = (NativeObject) jsObject;
            for (Object key : rawJsObject.keySet()) 
            {
                Object value = extractJSProperty(rawJsObject, key);

                //GC: 17/11/15 allow for UTC $date object
                if(key.equals("$date"))
                {
                    try
                    {
                        bsonObject = DateUtils.parseDate(value.toString(), 
                            new String[] {"yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"});
                    }
                    catch(java.text.ParseException e)
                    {
                        bson.put(key.toString(), convertJStoBSON(value, isJsObj, dateFormat));
                    }
                }
                else
                {
                    bson.put(key.toString(), convertJStoBSON(value, isJsObj, dateFormat));
                }
            }
        } else if (jsObject instanceof ScriptableMongoObject) {
            bsonObject = convertScriptableMongoToBSON(
                    (ScriptableMongoObject) jsObject, isJsObj, dateFormat);
        } else if (jsObject instanceof BaseFunction) {
            BaseFunction funcObject = (BaseFunction) jsObject;
            Object classPrototype = ScriptableObject.getClassPrototype(
                    funcObject, funcObject.getFunctionName());
            if ((classPrototype instanceof MinKey)
                    || (classPrototype instanceof MaxKey)) {
                // this is a special case handler for instances where MinKey or
                // MaxKey are provided without explicit constructor calls
                // index_check3.js does this
                bsonObject = convertScriptableMongoToBSON(
                        (ScriptableMongoObject) classPrototype, isJsObj, dateFormat);
            } else {
                // comes from eval calls
                String decompiledCode = (String) MongoRuntime
                        .call(new JSDecompileAction(funcObject));
                bsonObject = new Code(decompiledCode);
            }
        } else if (jsObject instanceof ScriptableObject) {
            // we found a ScriptableObject that isn't any of the concrete
            // ScriptableObjects above...
            String jsClassName = ((ScriptableObject) jsObject).getClassName();
            if ("Date".equals(jsClassName)) {
                Date dt = (Date)Context.jsToJava(jsObject, Date.class);
                bsonObject = dt;
                //GC: 18/11/15 use dateFormat parameter to format date fields
                if(dateFormat != null && dateFormat.length() > 0)
                    bsonObject = DateFormatUtils.formatUTC(dt, dateFormat);
            } else {
                Context.throwAsScriptRuntimeEx(new MongoScopeException(
                        "bsonizer couldnt convert js class: " + jsClassName));
                bsonObject = jsObject;
            }
        } else if (jsObject instanceof ConsString) {
            bsonObject = jsObject.toString();
        } else if (jsObject instanceof Undefined) {
            bsonObject = jsObject;
        } else if (jsObject instanceof Integer) {
            // this may seem strange, but JavaScript only knows about the number
            // type
            // which means in the official client we need to pass a Double
            // this applies to Long and Integer values
            bsonObject = Double.valueOf((Integer) jsObject);
        } else if (jsObject instanceof Long) {
            bsonObject = Double.valueOf((Long) jsObject);
        } else {
            bsonObject = jsObject;
        }

        return bsonObject;
    }

    @SuppressWarnings("deprecation")
    public static Object convertBSONtoJS(MongoScope mongoScope,
            Object bsonObject) {
        Object jsObject = null;
        if (bsonObject instanceof List<?>) {
            List<?> bsonList = (List<?>) bsonObject;
            Scriptable jsArray = (Scriptable) MongoRuntime
                    .call(new NewInstanceAction(mongoScope, bsonList.size()));

            int index = 0;
            for (Object bsonEntry : bsonList) {
                ScriptableObject.putProperty(jsArray, index,
                        convertBSONtoJS(mongoScope, bsonEntry));
                index++;
            }

            jsObject = jsArray;
        } else if (bsonObject instanceof BSONObject) {
            Scriptable jsObj = (Scriptable) MongoRuntime
                    .call(new NewInstanceAction(mongoScope));
            BSONObject bsonObj = (BSONObject) bsonObject;

            for (String key : bsonObj.keySet()) {
                Object value = convertBSONtoJS(mongoScope, bsonObj.get(key));
                MongoRuntime.call(new JSPopulatePropertyAction(jsObj, key,
                        value));
            }
            jsObject = jsObj;
        } else if (bsonObject instanceof Symbol) {
            jsObject = ((Symbol) bsonObject).getSymbol();
        } else if (bsonObject instanceof Date) {
            jsObject = MongoRuntime.call(new NewInstanceAction(mongoScope,
                    "Date", new Object[] { ((Date) bsonObject).getTime() }));
        } else if (bsonObject instanceof Pattern) {
            Pattern regex = (Pattern) bsonObject;
            String source = regex.pattern();
            String options = Bytes.regexFlags(regex.flags());
            jsObject = MongoRuntime.call(new NewInstanceAction(mongoScope,
                    "RegExp", new Object[] { source, options }));
        } else if (bsonObject instanceof org.bson.types.ObjectId) {
            jsObject = MongoRuntime.call(new NewInstanceAction(mongoScope,
                    "ObjectId"));
            ((ObjectId) jsObject)
                    .setRealObjectId((org.bson.types.ObjectId) bsonObject);
        } else if (bsonObject instanceof org.bson.types.MinKey) {
            jsObject = MongoRuntime.call(new NewInstanceAction(mongoScope,
                    "MinKey"));
        } else if (bsonObject instanceof org.bson.types.MaxKey) {
            jsObject = MongoRuntime.call(new NewInstanceAction(mongoScope,
                    "MaxKey"));
        } else if (bsonObject instanceof com.mongodb.DBRef) {
            com.mongodb.DBRef dbRef = (com.mongodb.DBRef) bsonObject;
            Object id = convertBSONtoJS(mongoScope, dbRef.getId());
            jsObject = MongoRuntime.call(new NewInstanceAction(mongoScope,
                    "DBRef", new Object[] { dbRef.getCollectionName(), id }));
        } else if (bsonObject instanceof BSONTimestamp) {
            BSONTimestamp bsonTstamp = (BSONTimestamp) bsonObject;
            jsObject = MongoRuntime.call(new NewInstanceAction(mongoScope,
                    "Timestamp", new Object[] { bsonTstamp.getTime(),
                            bsonTstamp.getInc() }));
        } else if (bsonObject instanceof Long) {
            jsObject = MongoRuntime.call(new NewInstanceAction(mongoScope,
                    "NumberLong"));
            ((NumberLong) jsObject).setRealLong((Long) bsonObject);
        } else if (bsonObject instanceof Integer) {
            jsObject = Double.valueOf((Integer) bsonObject);
        } else if (bsonObject instanceof Code) {
            jsObject = ((Code) bsonObject).getCode();
        } else if (bsonObject instanceof byte[]) {
            jsObject = MongoRuntime.call(new NewInstanceAction(mongoScope,
                    "BinData"));
            ((BinData) jsObject).setValues(0, (byte[]) bsonObject);
        } else if (bsonObject instanceof Binary) {
            jsObject = MongoRuntime.call(new NewInstanceAction(mongoScope,
                    "BinData"));
            ((BinData) jsObject).setValues(((Binary) bsonObject).getType(),
                    ((Binary) bsonObject).getData());
        } else if (bsonObject instanceof UUID) {
            jsObject = MongoRuntime.call(new NewInstanceAction(mongoScope,
                    "BinData"));
            UUID uuid = (UUID) bsonObject;
            ByteBuffer dataBuffer = ByteBuffer.allocate(16);
            // mongodb wire protocol is little endian
            dataBuffer.order(ByteOrder.LITTLE_ENDIAN);
            dataBuffer.putLong(uuid.getMostSignificantBits());
            dataBuffer.putLong(uuid.getLeastSignificantBits());
            ((BinData) jsObject).setValues(BSON.B_UUID, dataBuffer.array());
        } else {
            jsObject = bsonObject;
        }

        return jsObject;
    }

    /**
     * Ammended form of the {@link ScriptableObject#get(Object)} method that
     * will return {@link Undefined} property values instead of null.
     * 
     * @param jsObject
     * @param key
     * @return
     */
    private static Object extractJSProperty(ScriptableObject jsObject,
            Object key) {
        Object value = null;
        if (key instanceof String) {
            value = jsObject.get((String) key, jsObject);
        } else if (key instanceof Number) {
            value = jsObject.get(((Number) key).intValue(), jsObject);
        }
        if (value == Scriptable.NOT_FOUND) {
            return null;
        } else if (value instanceof Wrapper) {
            return ((Wrapper) value).unwrap();
        } else {
            return value;
        }
    }

    @SuppressWarnings("deprecation")
    private static Object convertScriptableMongoToBSON(
            ScriptableMongoObject jsMongoObj, boolean isJsObj, String dateFormat) {
        Object bsonObject = null;
        if (jsMongoObj instanceof ObjectId) {
            bsonObject = ((ObjectId) jsMongoObj).getRealObjectId();
        } else if (jsMongoObj instanceof BinData) {
            BinData binData = (BinData) jsMongoObj;
            byte type = new Integer(binData.getType()).byteValue();
            byte[] data = binData.getDataBytes();
            if (type == BSON.B_UUID) {
                ByteBuffer dataBuffer = ByteBuffer.wrap(data);
                // mongodb wire protocol is little endian
                dataBuffer.order(ByteOrder.LITTLE_ENDIAN);
                long mostSigBits = dataBuffer.getLong();
                long leastSigBits = dataBuffer.getLong();
                bsonObject = new UUID(mostSigBits, leastSigBits);
            } else
                bsonObject = new org.bson.types.Binary(type, data);
        } else if (jsMongoObj instanceof MinKey) {
            bsonObject = new org.bson.types.MinKey();
        } else if (jsMongoObj instanceof MaxKey) {
            bsonObject = new org.bson.types.MaxKey();
        } else if (jsMongoObj instanceof NumberInt) {
            bsonObject = Integer.valueOf(((NumberInt) jsMongoObj).getRealInt());
        } else if (jsMongoObj instanceof NumberLong) {
            bsonObject = Long.valueOf(((NumberLong) jsMongoObj).getRealLong());
        } else if (jsMongoObj instanceof DBRef) {
            DBRef jsRef = (DBRef) jsMongoObj;
            Object id = convertJStoBSON(jsRef.getId(), isJsObj, dateFormat);
            bsonObject = new com.mongodb.DBRef(jsRef.getNs(), id);
        } else if (jsMongoObj instanceof Timestamp) {
            bsonObject = convertTimestampToBSONTimestamp((Timestamp) jsMongoObj);
        }
        return bsonObject;
    }

    /**
     * seconds since epoch, used for Timestamp to BSONTimestamp conversion
     */
    private static int lastSecFromEpoch;

    /**
     * ordinal used for Timestamp to BSONTimestamp conversion
     */
    private static int timestampIncrementer = 1;

    private static synchronized BSONTimestamp convertTimestampToBSONTimestamp(
            Timestamp tstamp) {
        BSONTimestamp bsTstamp;
        int newTimeInSec = (int) tstamp.getT();
        if (newTimeInSec == 0) {
            newTimeInSec = (int) (new Date().getTime() / 1000);
            // seconds from epoch has changed, reset ordinal and set the new
            // lastSecFromEpoch value
            if (newTimeInSec != lastSecFromEpoch) {
                lastSecFromEpoch = newTimeInSec;
                timestampIncrementer = 1;
            } else
                timestampIncrementer++;
            bsTstamp = new BSONTimestamp(lastSecFromEpoch, timestampIncrementer);
        } else
            bsTstamp = new BSONTimestamp(newTimeInSec, (int) tstamp.getI());

        return bsTstamp;
    }

    private static class JSPopulatePropertyAction extends MongoAction {

        private Scriptable obj;
        private Object key;
        private Object value;

        public JSPopulatePropertyAction(Scriptable obj, Object key, Object value) {
            super(null);
            this.obj = obj;
            this.key = key;
            this.value = value;
        }

        @Override
        public Object doRun(Context cx) {
            return ScriptRuntime.setObjectElem(obj, key, value, cx);
        }

    }

    private static class JSDecompileAction extends MongoAction {

        private BaseFunction toDecompile;

        public JSDecompileAction(BaseFunction toDecompile) {
            super(null);
            this.toDecompile = toDecompile;
        }

        @Override
        public Object doRun(Context cx) {
            return cx.decompileFunction(toDecompile, 2);
        }

    }
}
