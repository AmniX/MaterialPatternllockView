# Material Pattern LockView

Material Pattern Lockview is a View which inspired from Lollipop+ Pattern lock.
This Project is still in work, More Options will be added as soon as possible. Some KeyFeature of this Library.

  - Ditto AOSP Pattern Lock LookAlike
  - WIP JavaDocs
  - StelthMode
  - Simple and CellList Callback
  - Diffrent and customizable Colors.
  - More and More things to come.


### Screenshots First
![enter image description here](https://vaadin.com/documents/10187/239105/Open+Source+logo/c40c6972-f6f7-4052-a197-1b442eabf8c4?t=1407413527014)

### Version
1.0.0

### Installation

    -Download or Clone this repo
    -You will find a folder name 'materiallockview' import it as a module in Android    Studio or Eclipse.
    -You are Good to Go, Code is Below.

```xml
<com.amnix.materiallockview.MaterialLockView
        xmlns:lockview="http://schemas.android.com/apk/res-auto"
        android:id="@+id/pattern"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_alignParentBottom="true"
        lockview:LOCK_COLOR="#fff"
        lockview:WRONG_COLOR="#ff0000"
        lockview:CORRECT_COLOR="#00ff00"/>
```
    -Use below Class to get a callback
```java
/**
     * The call back abstract class for detecting patterns entered by the user.
     */
    public static abstract class OnPatternListener {

        /**
         * A new pattern has begun.
         */
        public void onPatternStart() {

        }

        /**
         * The pattern was cleared.
         */
        public void onPatternCleared() {

        }

        /**
         * The user extended the pattern currently being drawn by one cell.
         *
         * @param pattern The pattern with newly added cell.
         */
        public void onPatternCellAdded(List<Cell> pattern, String SimplePattern) {

        }

        /**
         * A pattern was detected from the user.
         *
         * @param pattern The pattern.
         */
        public void onPatternDetected(List<Cell> pattern, String SimplePattern) {

        }
    }
```

### Todo's

* Add Many New Option
* Add a Clear WiKi
* Build AAR
* Many many more things


License
----
```
Copyright 2015 AmniX

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```