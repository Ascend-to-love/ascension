import java.util.ArrayList;
import java.util.List;
import java.lang.Math;
import java.lang.System;


import processing.data.Table;
import processing.data.TableRow;

import heronarts.lx.LX;
import heronarts.lx.model.LXAbstractFixture;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.output.LXOutput;
import heronarts.lx.pattern.LXPattern;

import com.heroicrobot.dropbit.registry.*;
import com.heroicrobot.dropbit.devices.pixelpusher.Strip;


class Model extends LXModel {

  final List<LED> leds;

  final Heart heart;
  final Leaves leaves;
  final Trunks trunks;
  final Roots roots;

  Model(Table ledData, Table ppStripData, DeviceRegistry ppRegistry) {
    super(new Fixture(ledData, ppStripData, ppRegistry));
    Fixture fixture = (Fixture)fixtures.get(0);
    leds = (List)fixture.getPoints();
    heart = fixture.heart;
    leaves = fixture.leaves;
    trunks = fixture.trunks;
    roots = fixture.roots;
  }

  static class Fixture extends LXAbstractFixture {
    final Heart heart;
    final Leaves leaves;
    final Trunks trunks;
    final Roots roots;
    Fixture(Table ledData, Table ppStripData, DeviceRegistry ppRegistry) {
      addPoints(heart = new Heart(ledData, ppStripData, ppRegistry));
      addPoints(leaves = new Leaves(ledData, ppStripData, ppRegistry));
      addPoints(trunks = new Trunks(ledData, ppStripData, ppRegistry));
      addPoints(roots = new Roots(ledData, ppStripData, ppRegistry));
    }
  }

}

class Heart extends LXModel {

  final List<HeartLED> leds;

  Heart(Table ledData, Table ppStripData, DeviceRegistry ppRegistry) {
    super(new Fixture(ledData, ppStripData, ppRegistry));
    Fixture fixture = (Fixture)fixtures.get(0);
    leds = (List)fixture.getPoints();
  }

  static class Fixture extends LXAbstractFixture {
    Fixture(Table ledData, Table ppStripData, DeviceRegistry ppRegistry) {
      for (TableRow row : ledData.rows()) {
        if (!row.getString("section").equals("heart")) continue;
        addPoint(new HeartLED(row, ppStripData, ppRegistry));
      }
    }
  }

}

class Leaves extends LXModel {

  final List<LeafLED> leds;

  Leaves(Table ledData, Table ppStripData, DeviceRegistry ppRegistry) {
    super(new Fixture(ledData, ppStripData, ppRegistry));
    Fixture fixture = (Fixture)fixtures.get(0);
    leds = (List)fixture.getPoints();
  }

  static class Fixture extends LXAbstractFixture {
    Fixture(Table ledData, Table ppStripData, DeviceRegistry ppRegistry) {
      for (TableRow row : ledData.rows()) {
        if (!(row.getString("section").equals("trunk")
            && row.getString("subsection").equals("leaf"))) continue;
        addPoint(new LeafLED(row, ppStripData, ppRegistry));
      }
    }
  }

}

class Trunks extends LXModel {

  final List<TrunkLED> leds;

  Trunks(Table ledData, Table ppStripData, DeviceRegistry ppRegistry) {
    super(new Fixture(ledData, ppStripData, ppRegistry));
    Fixture fixture = (Fixture)fixtures.get(0);
    leds = (List)fixture.getPoints();
  }

  static class Fixture extends LXAbstractFixture {
    Fixture(Table ledData, Table ppStripData, DeviceRegistry ppRegistry) {
  float lastMaxDist, maxDist;
  float totalLength = -1;
  int lastStripNumber = -1;

      for(int passNum = 0; passNum < 2; passNum++) {
        lastMaxDist = 0;
        maxDist = 0;

        for (TableRow row : ledData.rows()) {
          if (!(row.getString("section").equals("trunk")
              && !row.getString("subsection").equals("leaf"))) continue;

          if (passNum == 0 && row.getString("right_left").equals("left")) continue;
          
          if(row.getInt("led_number") == 0
             && lastStripNumber != row.getInt("strip_number")) {
            if(row.getInt("strip_number") == 1) {
              lastMaxDist = 0;
               maxDist = 0;
            } else {
              lastMaxDist = maxDist;
            }
          }
          lastStripNumber = row.getInt("strip_number");
          float thisDist = row.getFloat("arc_length") + lastMaxDist;

          if (passNum == 1) {
            addPoint(new TrunkLED(row, ppStripData, ppRegistry, thisDist, thisDist/totalLength));
          }

          maxDist = Math.max(maxDist,thisDist);
        }

        totalLength = maxDist;

      }
    }
  }

}

class Roots extends LXModel {

  final List<RootLED> leds;
  final int basePathCount;

  private static final String[] basePathOrdering = {
    "right-outside-back-zinner", "right-outside-back-zouter",
    "right-inside-back", "left-inside-back",
    "left-outside-back-zouter", "left-outside-back-zinner",
    "left-outside-front-zinner", "left-outside-front-zouter",
    "left-inside-front", "right-inside-front",
    "right-outside-front-zouter", "right-outside-front-zinner"
  };

  Roots(Table ledData, Table ppStripData, DeviceRegistry ppRegistry) {
    super(new Fixture(ledData, ppStripData, ppRegistry));
    Fixture fixture = (Fixture)fixtures.get(0);
    leds = (List)fixture.getPoints();
    basePathCount = fixture.basePathCount;
  }

  static class Fixture extends LXAbstractFixture {
    final int basePathCount;
    Fixture(Table ledData, Table ppStripData, DeviceRegistry ppRegistry) {
      for (TableRow row : ledData.rows()) {
        if (!row.getString("section").equals("root")) continue;
        addPoint(new RootLED(row, ppStripData, ppRegistry));
      }
      List<RootLED> leds = (List)getPoints();
      int basePathIndex = 0;
      boolean flip = true;
      for (String basePathString : basePathOrdering) {
        int stripCount = 0;
        for (RootLED led : leds) {
          if (led.basePathString.equals(basePathString)) {
            if (led.ledIndex+1 > stripCount) stripCount = led.ledIndex+1;
          }
        }
        for (RootLED led : leds) {
          if (led.basePathString.equals(basePathString)) {
            led.basePathIndex = basePathIndex + (flip ? stripCount-1-led.ledIndex : led.ledIndex);
          }
        }
        basePathIndex += stripCount;
        flip = !flip;
      }
      basePathCount = basePathIndex;
      for (RootLED led : leds) {
        led.normalizedBasePath = 1.0f * led.basePathIndex / basePathCount;
      }
    }
  }

}

class LED extends LXPoint {

  final String segmentId;

  final boolean isLeft;
  final int stripIndex;
  final int ledIndex;
  final int ppLedIndex;
  final int ppStripIndex;
  final int ppGroup;

  final DeviceRegistry ppRegistry;

  LED(TableRow row, Table ppStripData, DeviceRegistry registry) {
    super(row.getFloat("x"), row.getFloat("z"), -row.getFloat("y"));
    this.ppRegistry = registry;
    this.segmentId = row.getString("segment_id");
    this.isLeft = row.getString("right_left").equals("left");
    this.stripIndex = row.getInt("strip_number")-1;
    this.ledIndex = row.getInt("led_number");

    TableRow stripData = ppStripData.findRow(this.segmentId, "stripId");
    if (stripData != null) {
      if (stripData.getInt("reverse") == 1) {
        if (stripData.getInt("stripLength") <= this.ledIndex) {
          this.ppGroup = -1;
          this.ppLedIndex = -1;
          this.ppStripIndex = -1;
        } else {
          this.ppStripIndex = stripData.getInt("ppStrip");
          this.ppGroup = stripData.getInt("ppGroup");
          this.ppLedIndex = (stripData.getInt("stripLength") - this.ledIndex)
                            + stripData.getInt("indexOffset");
        }
      } else {
        if (stripData.getInt("stripLength") <= this.ledIndex) {
          this.ppGroup = -1;
          this.ppLedIndex = -1;
          this.ppStripIndex = -1;
        } else {
          this.ppStripIndex = stripData.getInt("ppStrip");
          this.ppGroup = stripData.getInt("ppGroup");
          this.ppLedIndex = this.ledIndex + stripData.getInt("indexOffset");
        }
      }
    } else {
      // not in the csv yet
      this.ppGroup = -1;
      this.ppLedIndex = -1;
      this.ppStripIndex = -1;
    }
  }

}

class HeartLED extends LED {
  final static float centerX = 0;
  final static float centerY = 5340;
  final static float centerZ = 0;

  final boolean isFront;
  final float radius2D, radius3D;
  final float thetaY, thetaZ;
  final float phiY, phiZ;
  final int heartShell;

  final static int[] firstShellBack = {
    0,    0,    0,    0,    0,
    8,    0,    8,    0,    8,

    0,    1,    3,    3,    3,
   10,    5,    8,    0,   12,

    3,    3,    9,    9,    0,
    3,    9,    3,    9,    0,

    4,    7,    3,    2,    3,
    5,    3,    5,    0,    3,

    7,    8,    0,    2,    7,
    8,    0,    2,    6,    0,

    0,    0,    0,    0,    0,
    0,
  };

  final static int[] firstShellFront = {
    9,    9,    9,    9,    9,
    9,    9,    9,    9,    9,

    9,    9,    9,    9,    9,
    9,    9,    9,    9,    9,

    9,    9,    9,    9,    9,
    9,    9,    9,    9,    9,

    9,    9,    9,    9,    9,
    9,    9,    9,    9,    9,

    9,    9,   10,   13,   14,
   15,   16,   17,   18,   18,

   18,   18,   18,   18,    18,
   18,
  };

  HeartLED(TableRow row, Table ppStripData, DeviceRegistry ppRegistry) {
    super(row, ppStripData, ppRegistry);
    this.isFront = row.getString("front_back").equals("front");
    
    float localX = this.x - this.centerX;
    float localY = this.y - this.centerY;
    float localZ = this.z - this.centerZ;
    
    this.radius2D = (float)Math.sqrt(
      Math.pow(localX, 2)
      + Math.pow(localY, 2)
    );
    
    this.radius3D = (float)Math.sqrt(
      Math.pow(localX, 2)
      + Math.pow(localY, 2)
      + Math.pow(localZ, 2)
    );
    this.thetaY = (float)Math.atan2(localX, -localZ);
    this.phiY = (float)Math.acos(localY/this.radius3D);
    this.thetaZ = (float)Math.atan2(-localX, localY);
    this.phiZ = (float)Math.acos(localZ/this.radius3D);

    if (this.isFront) {
      this.heartShell = this.ledIndex + firstShellFront[this.stripIndex];
    } else {
      this.heartShell = this.ledIndex + firstShellBack[this.stripIndex];
    }
  }

}

class LeafLED extends LED {

  LeafLED(TableRow row, Table ppStripData, DeviceRegistry ppRegistry) {
    super(row, ppStripData, ppRegistry);
  }

}

class TrunkLED extends LED {
  final float trunkDistance;
  final float normalizedTrunkDistance;
  final int phiIndex;

  TrunkLED(
          TableRow row,
          Table ppStripData,
          DeviceRegistry ppRegistry,
          float dist,
          float normDist) {
    super(row, ppStripData, ppRegistry);
    this.trunkDistance = dist;
    this.normalizedTrunkDistance = normDist;
    this.phiIndex = row.getInt("arc_strip_num");
  }

}

class RootLED extends LED {

  final boolean isInside;
  final boolean isFront;
  int basePathIndex;
  float normalizedBasePath;

  String basePathString;

  RootLED(TableRow row, Table ppStripData, DeviceRegistry ppRegistry) {
    super(row, ppStripData, ppRegistry);
    this.isInside = row.getString("subsection").equals("inside");
    this.isFront = row.getString("front_back").equals("front");
    this.basePathString = row.getString("right_left") + "-"
      + row.getString("subsection") + "-" + row.getString("front_back");
    if (!isInside) {
      this.basePathString += "-z" + (stripIndex < 3 ? "outer" : "inner");
    }
  }
}

class PixelPusherOutput extends LXOutput {

  private final Model model;

  PixelPusherOutput(LX lx) {
    super(lx);
    model = (Model)lx.model;
  }

  public void onSend(int[] colors) {
    for (LED led : model.leds) {
      pushColor(led, colors[led.index]);
    }
  }

  private void pushColor(LED led, int color) {
    if (led.ppStripIndex == -1) return;
    List<Strip> ppStrips = led.ppRegistry.getStrips(led.ppGroup);

    if (ppStrips.size() < led.ppStripIndex) return;

    Strip strip = ppStrips.get(led.ppStripIndex - 1);
    strip.setPixel(color, led.ppLedIndex);
  }

}

abstract class Pattern extends LXPattern {

  protected final Model model;

  public Pattern(LX lx) {
    super(lx);
    this.model = (Model)super.model;
  }

  public void setLEDColor(LED led, int c) {
    this.setColor(led.index, c);
  }

  public static final float PI = Utils.PI;
  public static final float HALF_PI = Utils.HALF_PI;
  public static final float THIRD_PI = Utils.THIRD_PI;
  public static final float QUARTER_PI = Utils.QUARTER_PI;
  public static final float TWO_PI = Utils.TWO_PI;

  public static final int millis() { return Utils.millis(); }
  public static final float abs(float n) { return Utils.abs(n); }
  public static final int abs(int n) { return Utils.abs(n); }
  public static final float sqrt(float n) { return Utils.sqrt(n); }
  public static final float pow(float n, float e) { return Utils.pow(n, e); }
  public static final int max(int a, int b) { return Utils.max(a, b); }
  public static final float max(float a, float b) { return Utils.max(a, b); }
  public static final int min(int a, int b) { return Utils.min(a, b); }
  public static final float min(float a, float b) { return Utils.min(a, b); }
  public static final int constrain(int amt, int low, int high) { return Utils.constrain(amt, low, high); }
  public static final float constrain(float amt, float low, float high) { return Utils.constrain(amt, low, high); }
  public static final float sin(float angle) { return Utils.sin(angle); }
  public static final float cos(float angle) { return Utils.cos(angle); }
  public static final float asin(float value) { return Utils.asin(value); }
  public static final float acos(float value) { return Utils.acos(value); }
  public static final float atan2(float y, float x) { return Utils.atan2(x, y); }
  public static final int ceil(float n) { return Utils.ceil(n); }
  public static final int floor(float n) { return Utils.floor(n); }
  public static final float lerp(float start, float stop, float amt) { return Utils.lerp(start, stop, amt); }
  public static final float random(float high) { return Utils.random(high); }
  public static final float random(float low, float high) { return Utils.random(low, high); }

}