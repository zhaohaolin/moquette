/*
 * CopyRight (c) 2012-2015 Hikvision Co, Ltd. All rights reserved. Filename:
 * SubscriptionUtils.java Creator: joe.zhao(zhaohaolin@hikvision.com.cn)
 * Create-Date: 下午1:40:41
 */
package org.eclipse.moquette.spi.impl.subscriptions;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO
 * 
 * @author joe.zhao(zhaohaolin@hikvision.com.cn)
 * @version $Id: SubscriptionUtils, v 0.1 2016年8月24日 下午1:40:41 Exp $
 */
public abstract class SubscriptionUtils {
	
	private static final Logger	LOG	= LoggerFactory
											.getLogger(SubscriptionUtils.class);
	
	/**
	 * Verify if the 2 topics matching respecting the rules of MQTT Appendix A
	 */
	// TODO reimplement with iterators or with queues
	public final static boolean matchTopics(String msgTopic,
			String subscriptionTopic) {
		try {
			List<Token> msgTokens = parseTopic(msgTopic);
			List<Token> subscriptionTokens = parseTopic(subscriptionTopic);
			int i = 0;
			for (; i < subscriptionTokens.size(); i++) {
				Token subToken = subscriptionTokens.get(i);
				if (subToken != Token.MULTI && subToken != Token.SINGLE) {
					if (i >= msgTokens.size()) {
						return false;
					}
					Token msgToken = msgTokens.get(i);
					if (!msgToken.equals(subToken)) {
						return false;
					}
				} else {
					if (subToken == Token.MULTI) {
						return true;
					}
					if (subToken == Token.SINGLE) {
						// skip a step forward
					}
				}
			}
			// if last token was a SINGLE then treat it as an empty
			// if (subToken == Token.SINGLE && (i - msgTokens.size() == 1)) {
			// i--;
			// }
			return i == msgTokens.size();
		} catch (ParseException ex) {
			LOG.error(null, ex);
			throw new RuntimeException(ex);
		}
	}
	
	public final static List<Token> parseTopic(String topic)
			throws ParseException {
		List<Token> tokens = new ArrayList<Token>();
		String[] splitted = topic.split("/");
		
		if (splitted.length == 0) {
			tokens.add(Token.EMPTY);
		}
		
		if (topic.endsWith("/")) {
			// Add a fictious space
			String[] newSplitted = new String[splitted.length + 1];
			System.arraycopy(splitted, 0, newSplitted, 0, splitted.length);
			newSplitted[splitted.length] = "";
			splitted = newSplitted;
		}
		
		for (int i = 0; i < splitted.length; i++) {
			String s = splitted[i];
			if (s.isEmpty()) {
				// if (i != 0) {
				// throw new
				// ParseException("Bad format of topic, expetec topic name between separators",
				// i);
				// }
				tokens.add(Token.EMPTY);
			} else if (s.equals("#")) {
				// check that multi is the last symbol
				if (i != splitted.length - 1) {
					throw new ParseException(
							"Bad format of topic, the multi symbol (#) has to be the last one after a separator",
							i);
				}
				tokens.add(Token.MULTI);
			} else if (s.contains("#")) {
				throw new ParseException(
						"Bad format of topic, invalid subtopic name: " + s, i);
			} else if (s.equals("+")) {
				tokens.add(Token.SINGLE);
			} else if (s.contains("+")) {
				throw new ParseException(
						"Bad format of topic, invalid subtopic name: " + s, i);
			} else {
				tokens.add(new Token(s));
			}
		}
		
		return tokens;
	}
	
	/**
	 * Check if the topic filter of the subscription is well formed
	 * */
	public final static boolean validate(String topicFilter) {
		try {
			List<Token> list = parseTopic(topicFilter);
			if (null == list || list.isEmpty()) {
				return false;
			}
			return true;
		} catch (ParseException pex) {
			LOG.info("Bad matching topic filter <{}>", topicFilter);
			return false;
		}
	}
	
}
