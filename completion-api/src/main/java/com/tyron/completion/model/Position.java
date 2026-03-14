package com.tyron.completion.model;

/** Represents the position in the editor in terms of lines and columns */
public class Position implements Comparable<Position> {

  public static final Position NONE = new Position(-1, -1);

  public int line;

  public int column;

  public long start;
  public long end;

  public int index;

  public Position(long start, long end) {
    this.start = start;
    this.end = end;
    line = -1;
    column = -1;
    index = -1;
  }

  public Position(int line, int column) {
    this.line = line;
    this.column = column;
    start = -1;
    end = -1;
  }

  public Position(int line, int column, int index) {
    this.line = line;
    this.column = column;
    this.index = index;
    start = -1;
    end = -1;
  }

  public int requireIndex() {
    if (index == -1) {
      throw new IllegalArgumentException("No index provided");
    }
    return index;
  }

  public void zeroIfNegative() {
    if (line < 0) {
      line = 0;
    }

    if (column < 0) {
      column = 0;
    }
  }

  public void setLine(int line) {
    this.line = line;
  }

  public void setColumn(int column) {
    this.column = column;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public void setStart(long start) {
    this.start = start;
  }

  public void setEnd(long end) {
    this.end = end;
  }

  public int getLine() {
    return line;
  }

  public int getColumn() {
    return column;
  }

  public int getIndex() {
    return index;
  }

  public long getStart() {
    return start;
  }

  public long getEnd() {
    return end;
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof Position)) {
      return false;
    }
    Position that = (Position) object;
    return (this.line == that.line && this.column == that.column);
  }

  @Override
  public int compareTo(Position other) {
    int byLine = Integer.compare(this.line, other.line);
    if (byLine != 0) {
      return byLine;
    }
    return Integer.compare(this.column, other.column);
  }

  @Override
  public int hashCode() {
    var result = line;
    result = 31 * result + column;
    return result;
  }
}
