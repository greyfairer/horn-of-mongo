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
package org.github.nlloyd.hornofmongo.adaptor;

import org.mozilla.javascript.annotations.JSConstructor;
import org.mozilla.javascript.annotations.JSGetter;
import org.mozilla.javascript.annotations.JSSetter;

/**
 * @author nlloyd
 *
 */
public class DBPointer extends ScriptableMongoObject {

    /**
     * 
     */
    private static final long serialVersionUID = -9106146988587782504L;
    
    private String ns;
    private ObjectId id;
    
    public DBPointer() {
        super();
    }
    
    @JSConstructor
    public DBPointer(String ns, ObjectId id) {
        super();
        this.ns = ns;
        this.id = id;
//        put("ns", this, ns);
//        put("id", this, id);
    }

    /**
     * @see org.mozilla.javascript.ScriptableObject#getClassName()
     */
    @Override
    public String getClassName() {
        return this.getClass().getSimpleName();
    }

    /**
     * @return the ns
     */
    @JSGetter
    public String getNs() {
        return ns;
    }

    /**
     * @param ns the ns to set
     */
    @JSSetter
    public void setNs(String ns) {
        this.ns = ns;
    }

    /**
     * @return the id
     */
    @JSGetter
    public ObjectId getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    @JSSetter
    public void setId(ObjectId id) {
        this.id = id;
    }

}
