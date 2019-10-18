package com.tivo.exoplayer.library;// Copyright 2010 TiVo Inc.  All rights reserved.

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class SimpleSlidingGraph extends View implements Runnable {

  public static final int VERTICAL_PADDING = 2;
  private int backgroundColor;
  private float currentHeight = -1.0f;
  private float currentWidth = -1.0f;

  private List<TraceLine> traces;

  public static final float MAX_X = 200.0f;

  private class TraceLine {
    private Path path;
    private Paint paint;
    private float minValue;
    private float maxValue;
    private float currentX;

    TraceLine(int color, float minValue, float maxValue) {
      this.minValue = minValue;
      this.maxValue = maxValue;
      paint = new Paint();
      path = new Path();
      paint.setAntiAlias(true);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(2);
      paint.setColor(color);
      currentX = 0;
    }

    public void drawOn(Canvas canvas) {
      canvas.drawPath(path, paint);
    }


    public void addDataPoint(float value) {
      if (path.isEmpty()) {
        path.moveTo(0, currentHeight);
      }
      float x = timeToPixels(++currentX);
      path.lineTo(x, valueToPixels(value));

      if (value > this.maxValue) {
        Matrix rescale = new Matrix();
        rescale.setScale(1.0f, value / this.maxValue);
        path.transform(rescale);
        this.maxValue = value;
      }

      // Flush our old path (good place to not let it grow infinately in size
      // and position back to the start.
      //  TODO - could implement some sliding behavior, but not really worth it.
      if (x > (.95 * currentWidth)) {
        currentX = 0;
        path.reset();
        path.moveTo(0, currentHeight);
      }
    }

    private float valueToPixels(float value) {
      return currentHeight - (value * currentHeight / (maxValue - minValue));
    }

    private float timeToPixels(float time) {
      return time * currentWidth / MAX_X;
    }
  }

  public SimpleSlidingGraph(Context context, AttributeSet attrs) {
    super(context, attrs);

    backgroundColor = Color.GRAY;
    setBackgroundColor(backgroundColor);

    traces = new ArrayList<>();

  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);

    this.currentHeight = h - VERTICAL_PADDING;
    this.currentWidth = w;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    for (TraceLine traceLine : traces) {
      traceLine.drawOn(canvas);
    }
  }

  @Override
  public void run() {
    invalidate();
    postDelayed(this, 1000);
  }

  public int addTraceLine(int color, float minValue, float maxValue) {
    traces.add(new TraceLine(color, minValue, maxValue));
    return traces.size() - 1;
  }

  public void addDataPoint(float value, int traceNumber) {
    if (traces.size() > traceNumber && currentHeight > 0.0f) {
      traces.get(traceNumber).addDataPoint(value);
      invalidate();
    }
  }
}
