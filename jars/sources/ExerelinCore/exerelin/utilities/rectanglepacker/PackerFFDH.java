package exerelin.utilities.rectanglepacker;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class PackerFFDH<T extends Rectangle> extends Packer<T>{
    private List<StripLevel> levels = new ArrayList<>(1);
    private int top = 0;

    public PackerFFDH(int stripWidth, List<T> rectangles){
        super(stripWidth, rectangles);
    }
    @Override
    public List<T> pack() {
        this.sortByNonIncreasingHeight(rectangles);
        for (Rectangle r : rectangles){
            boolean fitsOnALevel = false;
            for (StripLevel level : levels){
                fitsOnALevel = level.fitRectangle(r);
                if (fitsOnALevel) break;
            }
            if (!fitsOnALevel){
                StripLevel level = new StripLevel(stripWidth, top);
                level.fitRectangle(r);
                levels.add(level);
                top += r.height;
            }
        }
        return this.rectangles;
    }
}
