package com.intellij.formatting;

import org.jetbrains.annotations.NonNls;

class SpacingImpl extends Spacing {
  private int myMinSpaces;
  private int myKeepBlankLines;
  private int myMaxSpaces;
  private int myMinLineFeeds;
  private int myPrefLineFeeds = 0;
  protected int myFlags;

  private static final int READ_ONLY_MASK = 1;
  private static final int SAFE_MASK = 2;
  private static final int SHOULD_KEEP_LINEBEAKS_MASK = 4;
  private static final int SHOULD_KEEP_FIRST_COLUMN_MASK = 8;

  public SpacingImpl(final int minSpaces,
                     final int maxSpaces,
                     final int minLineFeeds,
                     final boolean isReadOnly,
                     final boolean safe,
                     final boolean shouldKeepLineBreaks,
                     final int keepBlankLines,
                     final boolean keepFirstColumn,
                     final int prefLineFeeds) {
    init(minSpaces, maxSpaces, minLineFeeds, isReadOnly, safe, shouldKeepLineBreaks, keepBlankLines, keepFirstColumn, prefLineFeeds);
  }

  void init(final int minSpaces, final int maxSpaces, final int minLineFeeds, final boolean isReadOnly, final boolean safe, final boolean shouldKeepLineBreaks,
            final int keepBlankLines,
            final boolean keepFirstColumn,
            final int prefLineFeeds) {
    myMinSpaces = minSpaces;

    myMaxSpaces = Math.max(minSpaces, maxSpaces);
    myMinLineFeeds = minLineFeeds;
    myPrefLineFeeds = prefLineFeeds;
    if (minLineFeeds > 1 && (minLineFeeds - 1) > keepBlankLines) {
      myKeepBlankLines = minLineFeeds - 1;
    } else {
      myKeepBlankLines = keepBlankLines;
    }
    myFlags = (isReadOnly ? READ_ONLY_MASK:0) | (safe ? SAFE_MASK:0) | (shouldKeepLineBreaks ? SHOULD_KEEP_LINEBEAKS_MASK:0) |
      (keepFirstColumn ? SHOULD_KEEP_FIRST_COLUMN_MASK:0);
  }

  int getMinSpaces() {
    return myMinSpaces;
  }

  int getMaxSpaces() {
    return myMaxSpaces;
  }

  int getMinLineFeeds() {
    return myMinLineFeeds;
  }

  final boolean isReadOnly(){
    return (myFlags & READ_ONLY_MASK) != 0;
  }

  final boolean containsLineFeeds() {
    return myMinLineFeeds > 0;
  }

  public final boolean isSafe() {
    return (myFlags & SAFE_MASK) != 0;
  }

  public void refresh(FormatProcessor formatter) {
  }

  public final boolean shouldKeepLineFeeds() {
    return (myFlags & SHOULD_KEEP_LINEBEAKS_MASK) != 0;
  }

  public int getKeepBlankLines() {
    return myKeepBlankLines;
  }

  public final boolean shouldKeepFirstColumn() {
    return (myFlags & SHOULD_KEEP_FIRST_COLUMN_MASK) != 0;
  }

  public boolean equals(Object o) {
    if (!(o instanceof SpacingImpl)) return false;
    final SpacingImpl spacing = (SpacingImpl)o;
    return myFlags == spacing.myFlags &&
           myMinSpaces == spacing.myMinSpaces &&
           myMaxSpaces == spacing.myMaxSpaces &&
           myMinLineFeeds == spacing.myMinLineFeeds &&
           myPrefLineFeeds == spacing.myPrefLineFeeds &&
           myKeepBlankLines == spacing.myKeepBlankLines;
  }

  public int hashCode() {
    return myMinSpaces + myMaxSpaces * 29 + myMinLineFeeds * 11 + myFlags + myKeepBlankLines + myPrefLineFeeds;
  }

  @NonNls
  @Override
  public String toString() {
    return "<Spacing: minSpaces=" + myMinSpaces + " maxSpaces=" + myMaxSpaces + " minLineFeeds=" + myMinLineFeeds + ">";
  }

  public int getPrefLineFeeds() {
    return myPrefLineFeeds >= myMinLineFeeds ? myPrefLineFeeds : myMinLineFeeds;
  }
}