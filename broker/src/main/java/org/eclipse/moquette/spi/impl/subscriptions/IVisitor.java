/*
 * CopyRight (c) 2012-2015 Hikvision Co, Ltd. All rights reserved.
 * Filename:    IVisitor.java
 * Creator:     joe.zhao(zhaohaolin@hikvision.com.cn)
 * Create-Date: 下午1:46:31
 */
package org.eclipse.moquette.spi.impl.subscriptions;

/**
 * TODO
 * 
 * @author joe.zhao(zhaohaolin@hikvision.com.cn)
 * @version $Id: IVisitor, v 0.1 2016年8月24日 下午1:46:31 Exp $
 */
public interface IVisitor<T> {
	void visit(TreeNode node, int deep);
	
	T getResult();
}
