package com.tivo.exoplayer.library;// Copyright 2010 TiVo Inc.  All rights reserved.

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class SimpleSlidingGraph extends View implements Runnable {


  public static final int STEP = 4;
  private Paint filled = new Paint();
  private int currentTime = 0;
  private boolean wrapped = false;
  private int backgroundColor;

  private Path pathList[] = new Path[2];

  public SimpleSlidingGraph(Context context, AttributeSet attrs) {
    super(context, attrs);

    backgroundColor = Color.BLACK;
    setBackgroundColor(backgroundColor);

    pathList[0] = new Path();
    pathList[1] = new Path();

    filled.setStrokeWidth(STEP);
    filled.setColor(0xFF20fe00);

    pathList[0].moveTo(0, 0);
    int i = 0;
    for (float angle=1; angle < (2 * Math.PI) * 4 ; angle += 0.083775804095728) {
      float sin = (float) Math.sin(angle);
      float y = 40 * sin;
      pathList[0].lineTo(i++, y);
    }
    postDelayed(this, 1000);
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
  }

  @Override
  protected void onDraw(Canvas canvas) {

    canvas.drawPath(pathList[0], filled);

  }

  //
//  @Override
//  protected void onDraw(Canvas canvas) {
//
//    if (currentTime >= canvas.getWidth()) {
//      currentTime = 0;
//      wrapped = true;
//    } else if (wrapped && currentTime < (canvas.getWidth() - STEP)) {
//
//      // Black out the oldest line of data
//
//      filled.setColor(backgroundColor);
//      canvas.drawLine(currentTime - STEP, 0, currentTime - STEP, canvas.getHeight(), filled);
//    }
//
//    int red = 0;
//    int green = 0;
//    int blue = 0;
//
//    if (((currentTime / 4) % 2) == 0) {
//      blue = 0; green = 255;
//    } else {
//      blue = 255; green = 0;
//    }
//
//    filled.setColor(Color.rgb(red, green, blue));
//    canvas.drawLine(currentTime, 0, currentTime, canvas.getHeight(), filled);
//  }


  @Override
  public void run() {
    Matrix matrix = new Matrix();
    matrix.postTranslate(5, 0);
    pathList[0].transform(matrix);
    invalidate();
    postDelayed(this, 1000);
  }
}
