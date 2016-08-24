/*
 * CopyRight (c) 2012-2015 Hikvision Co, Ltd. All rights reserved.
 * Filename:    ISubscriptionsStore.java
 * Creator:     joe.zhao(zhaohaolin@hikvision.com.cn)
 * Create-Date: 下午6:56:38
 */
package org.eclipse.moquette.spi;

import java.util.List;

import org.eclipse.moquette.spi.impl.subscriptions.Subscription;

/**
 * TODO
 * 
 * @author joe.zhao(zhaohaolin@hikvision.com.cn)
 * @version $Id: ISubscriptionsStore, v 0.1 2016年8月24日 下午6:56:38 Exp $
 */
public interface ISubscriptionsStore {
	
	void init(final ISessionsStore sessionsStore);
	
	void add(Subscription newSubscription);
	
	void activate(String clientID);
	
	void deactivate(String clientID);
	
	void removeForClient(String clientID);
	
	void removeSubscription(String topic, String clientID);
	
	List<Subscription> matches(String topic);
	
	String dumpTree();
	
}
