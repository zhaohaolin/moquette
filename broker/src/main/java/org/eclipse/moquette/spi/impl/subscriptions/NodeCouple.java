/*
 * CopyRight (c) 2012-2015 Hikvision Co, Ltd. All rights reserved. Filename:
 * NodeCouple.java Creator: joe.zhao(zhaohaolin@hikvision.com.cn) Create-Date:
 * 下午1:47:52
 */
package org.eclipse.moquette.spi.impl.subscriptions;

/**
 * TODO
 * 
 * @author joe.zhao(zhaohaolin@hikvision.com.cn)
 * @version $Id: NodeCouple, v 0.1 2016年8月24日 下午1:47:52 Exp $
 */
public class NodeCouple {
	
	final TreeNode	root;
	final TreeNode	createdNode;
	
	public NodeCouple(TreeNode root, TreeNode createdNode) {
		this.root = root;
		this.createdNode = createdNode;
	}
	
}
