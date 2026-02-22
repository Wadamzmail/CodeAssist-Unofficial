package com.tyron.completion.model;

public class Range {
  public Position start, end;

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
  
  public Range pointRange(int line,int column){
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

  public static final Range NONE = new Range(Position.NONE, Position.NONE);
}
