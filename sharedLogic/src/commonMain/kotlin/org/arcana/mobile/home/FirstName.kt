package org.arcana.mobile.home

/** The member's first name for the greeting — the display name up to the first
 *  space. Members provide a real name at signup ("Cole Tomlinson" -> "Cole"); a
 *  single-token name is returned as-is. */
fun firstName(displayName: String): String = displayName.trim().substringBefore(' ')
