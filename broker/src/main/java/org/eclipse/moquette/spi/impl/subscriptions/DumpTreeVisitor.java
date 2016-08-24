/*
 * CopyRight (c) 2012-2015 Hikvision Co, Ltd. All rights reserved. Filename:
 * DumpTreeVisitor.java Creator: joe.zhao(zhaohaolin@hikvision.com.cn)
 * Create-Date: 下午1:47:03
 */
package org.eclipse.moquette.spi.impl.subscriptions;


/**
 * TODO
 * 
 * @author joe.zhao(zhaohaolin@hikvision.com.cn)
 * @version $Id: DumpTreeVisitor, v 0.1 2016年8月24日 下午1:47:03 Exp $
 */
public class DumpTreeVisitor implements IVisitor<String> {
	
	String	s	= "";
	
	@Override
	public void visit(TreeNode node, int deep) {
		String subScriptionsStr = "";
		String indentTabs = indentTabs(deep);
		for (Subscription sub : node.subscriptions) {
			subScriptionsStr += indentTabs + sub.toString() + "\n";
		}
		s += node.getToken() == null ? "" : node.getToken().toString();
		s += "\n" + (node.subscriptions.isEmpty() ? indentTabs : "")
				+ subScriptionsStr /* + "\n" */;
	}
	
	private String indentTabs(int deep) {
		String s = "";
		for (int i = 0; i < deep; i++) {
			s += "\t";
			// s += "--";
		}
		return s;
	}
	
	@Override
	public String getResult() {
		return s;
	}
}
