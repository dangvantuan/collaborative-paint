/**
 * 
 */
package com.acme.collpaint.client;

import com.acme.collpaint.client.comet.CometSessionException;
import com.acme.collpaint.client.comet.CometSessionsSupport;
import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;


/**
 * <dl>
 * <dt>Project:</dt> <dd>collaborative-paint</dd>
 * <dt>Package:</dt> <dd>com.acme.collpaint.client</dd>
 * </dl>
 *
 * <code>CollPaintService</code>
 *
 * <p>Description</p>
 *
 * @author Ulric Wilfred <shaman.sir@gmail.com>
 * @date May 20, 2011 11:47:55 PM 
 *
 */
@RemoteServiceRelativePath("service")
public interface CollPaintService extends RemoteService, CometSessionsSupport {
    
    public void updateLine(LineUpdate data) throws CometSessionException, CollPaintException;
    
}
