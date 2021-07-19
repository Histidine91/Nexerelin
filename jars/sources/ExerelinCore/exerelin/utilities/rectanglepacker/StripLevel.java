package exerelin.utilities.rectanglepacker;

import java.awt.*;

class StripLevel{
    private int width, availableWidth, top;

    StripLevel(int width, int top){
        this.width = width;
        this.availableWidth = width;
        this.top = top;
    }

    boolean fitRectangle(Rectangle r){
        int leftOver = availableWidth - r.width;
        if (leftOver >= 0){
            r.setLocation(width - availableWidth, top);
            this.availableWidth = leftOver;
            return true;
        }
        return false;
    }

    int availableWidth(){
        return this.availableWidth;
    }

    boolean canFit(Rectangle r){
        return this.availableWidth - r.width >= 0;
    }
}