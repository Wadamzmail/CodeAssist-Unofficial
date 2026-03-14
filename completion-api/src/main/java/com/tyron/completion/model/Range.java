package com.tyron.completion.model;

public class Range implements Comparable<Range> {
  public Position start, end;
  public static final Range NONE = new Range(Position.NONE, Position.NONE);

  public Range(long startPosition, long endPosition) {
    start = new Position(startPosition, startPosition);
    end = new Position(endPosition, endPosition);
  }

  public Range(Position start, Position end) {
    this.start = start;
    this.end = end;
  }

  public void validate() {
    start.zeroIfNegative();
    end.zeroIfNegative();
  }

  public Range pointRange(int line, int column) {
    return pointRange(new Position(line, column));
  }

  public Range pointRange(Position position) {
    return new Range(position, position);
  }

  public void setStart(Position start) {
    this.start = start;
  }

  public void setEnd(Position end) {
    this.end = end;
  }

  public Position getStart() {
    return start;
  }

  public Position getEnd() {
    return end;
  }

  @Override
  public String toString() {
    return start + "-" + end;
  }

  @Override
  public int hashCode() {
    var result = start.hashCode();
    result = 31 * result + end.hashCode();
    return result;
  }

  @Override
  public int compareTo(Range other) {
    return start.compareTo(other.start);
  }

  public int compareByEnd(Range other) {
    return end.compareTo(other.end);
  }

  public boolean contains(Position position) {
    if (position.line < start.line || position.line > end.line) {
      return false;
    }

    if (start.line == end.line) {
      return position.column >= start.column && position.column <= end.column;
    }

    return false;
  }

  public int containsForBinarySearch(Position position) {

    // The position might appear before this range
    if (position.line < start.line) {
      return -1;
    }

    // The position might appear after this range
    if (position.line > end.line) {
      return 1;
    }

    // If start and end lines are same, compare by column indexes
    if (start.line == end.line) {

      if (position.column < start.column) {
        return -1;
      }

      if (position.column > end.column) {
        return 1;
      }
    }

    // This range definitely contains the position.
    return 0;
  }

  public boolean containsLine(int line) {
    return start.line <= line && end.line >= line;
  }

  public boolean containsColumn(int column) {
    return start.column <= column && end.column >= column;
  }

  public boolean containsRange(Range other) {
    if (!containsLine(other.start.line) || !containsLine(other.end.line)) {
      return false;
    }

    return containsColumn(other.start.column) && containsColumn(other.end.column);
  }

  public boolean isSmallerThan(Range other) {
    return other.isBiggerThan(this);
  }

  public boolean isBiggerThan(Range other) {

    if (equals(other)) {
      return false;
    }

    if (start.line < other.start.line && end.line > other.end.line) {
      return true;
    }

    if (start.line == other.start.line && end.line == other.end.line) {
      if (start.column <= other.start.column && end.column >= other.end.column) {
        return true;
      }
    }

    return false;
  }
}
