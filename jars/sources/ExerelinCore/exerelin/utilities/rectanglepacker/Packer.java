package exerelin.utilities.rectanglepacker;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class Packer<T extends Rectangle>{
    int stripWidth;
    List<T> rectangles;

    Packer(int stripWidth, List<T> rectangles){
        this.stripWidth = stripWidth;
        this.rectangles = rectangles;
    }

    public static <U extends Rectangle> List<U> pack(List<U> rectangles, Algorithm algorithm, int stripWidth){
        Packer<U> packer;
        switch(algorithm){
        case FIRST_FIT_DECREASING_HEIGHT:
            packer = new PackerFFDH<>(stripWidth, rectangles);
            return packer.pack();
        case NEXT_FIT_DECREASING_HEIGHT:
            packer = new PackerNFDH<>(stripWidth, rectangles);
            return packer.pack();
        case BEST_FIT_DECREASING_HEIGHT:
            packer = new PackerBFDH<>(stripWidth, rectangles);
            return packer.pack();
        default:
            return new ArrayList<>();
        }
    }

    public abstract List<T> pack();

    void sortByNonIncreasingHeight(List<T> rectangles){
        Collections.sort(rectangles, new NonIncreasingHeightRectangleComparator());
    }

    private class NonIncreasingHeightRectangleComparator implements Comparator<Rectangle>{
        @Override
        public int compare(Rectangle o1, Rectangle o2) {
            return Integer.compare(o2.height, o1.height);
        }
    }

    public enum Algorithm{
        FIRST_FIT_DECREASING_HEIGHT,
        NEXT_FIT_DECREASING_HEIGHT,
        BEST_FIT_DECREASING_HEIGHT
    }

    public static Algorithm[] getAllAlgorithms(){
        return new Algorithm[]{
                Algorithm.FIRST_FIT_DECREASING_HEIGHT,
                Algorithm.NEXT_FIT_DECREASING_HEIGHT,
                Algorithm.BEST_FIT_DECREASING_HEIGHT
        };
    }
}
