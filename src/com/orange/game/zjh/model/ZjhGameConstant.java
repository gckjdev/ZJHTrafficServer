
package com.orange.game.zjh.model;

import com.orange.game.constants.DBConstants;


public class ZjhGameConstant {
	
	public static final String ZJH_GAME_ID = DBConstants.GAME_ID_ZJH;
	
	public static final int PER_USER_CARD_NUM = 3; // each user has 3 cards;
	public static final int ALL_CARD_NUM = 52;
	public static final int PER_SUIT_NUM = 13; // 2-10 ,J, Q ,K
	public static final int SUIT_TYPE_NUM = 4; // spade, heart, club, diamond
	
	// for card type
	public static final int RANK_MASK = (1 << (PER_SUIT_NUM)) - 1; // binary: 11111111111, from left to right, each bit
																					 // represents A, K, Q, J, 10, 9, ..., 2
	public static final int SUIT_MASK = (1 << (SUIT_TYPE_NUM)) - 1; // binary: 1111,from left to right, each bit represents spade, club, heart, diamond
	
	public static final int TYPE_SPECIAL = 0x1FF4;  // 1 1111 1111 0100

	public static final int USER_INFO_AUTO_BET 		      = 0x1;    // 00 0000 0000 0001
	public static final int USER_INFO_CHECKED_CARD        = 0x2;    // 00 0000 0000 0010
	public static final int USER_INFO_FOLDED_CARD         = 0x4;    // 00 0000 0000 0100
	public static final int USER_INFO_SHOWED_CARD         = 0x8;    // 00 0000 0000 1000
	public static final int USER_INFO_LOSED_GAME          = 0x10;   // 00 0000 0001 0000
	public static final int USER_INFO_ACTION_NONE         = 0x20;   // 00 0000 0010 0000
	public static final int USER_INFO_ACTION_BET          = 0x40;   // 00 0000 0100 0000
	public static final int USER_INFO_ACTION_RAISE_BET    = 0x80;   // 00 0000 1000 0000
	public static final int USER_INFO_ACTION_AUTO_BET     = 0x100;  // 00 0001 0000 0000
	public static final int USER_INFO_ACTION_CHECK_CARD   = 0x200;  // 00 0010 0000 0000
	public static final int USER_INFO_ACTION_FOLD_CARD    = 0x400;  // 00 0100 0000 0000
	public static final int USER_INFO_ACTION_COMPARE_CARD = 0x800;  // 00 1000 0000 0000
	public static final int USER_INFO_ACTION_SHOW_CARD    = 0x1000; // 01 0000 0000 0000
	public static final int USER_INFO_ACTION_CHANGE_CARD  = 0x2000; // 10 0000 0000 0000
	public static final int USER_INFO_INITIAL_VALUE 		= 0x20;   // 00 0000 0010 0000, only set ACTION_NONE
	public static final int LAST_ACTION_MASK 			= 0x1F; 	 // 11 1111 1110 0000, used to clear last action

	public static final int COMPARE_LOSS = 20;

	
	
}
