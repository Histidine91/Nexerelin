package exerelin.utilities.rectanglepacker;

import java.awt.*;
import java.util.List;

class PackerNFDH<T extends Rectangle> extends Packer<T>{
    private StripLevel currentLevel;

    public PackerNFDH(int stripWidth, List<T> rectangles){
        super(stripWidth, rectangles);
    }

    @Override
    public List<T> pack() {
        this.sortByNonIncreasingHeight(rectangles);
        int top = 0;
        for (Rectangle r : rectangles){
            if (currentLevel == null || !currentLevel.fitRectangle(r)){
                currentLevel = new StripLevel(this.stripWidth, top);
                currentLevel.fitRectangle(r);
                top += r.height;
            }
        }
        return this.rectangles;
    }
}
