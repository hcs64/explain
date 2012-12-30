var kWidth = 640;
var kHeight= 480;

function supports_canvas() {
    return !!document.createElement('canvas').getContext;
}

function supports_canvas_text() {
    if (!supports_canvas()) { return false; }
    var dummy_canvas = document.createElement('canvas');
    var context = dummy_canvas.getContext('2d');
    return typeof context.fillText == 'function';
}

// http://paulirish.com/2011/requestanimationframe-for-smart-animating/
// http://my.opera.com/emoller/blog/2011/12/20/requestanimationframe-for-smart-er-animating

// requestAnimationFrame polyfill by Erik Möller
// fixes from Paul Irish and Tino Zijdel
(function() {
    var lastTime = 0;
    var vendors = ['ms', 'moz', 'webkit', 'o'];
    for(var x = 0; x < vendors.length && !window.requestAnimationFrame; ++x) {
        window.requestAnimationFrame = window[vendors[x]+'RequestAnimationFrame'];
        window.cancelAnimationFrame = 
          window[vendors[x]+'CancelAnimationFrame'] || window[vendors[x]+'CancelRequestAnimationFrame'];
    }
 
    if (!window.requestAnimationFrame)
        window.requestAnimationFrame = function(callback, element) {
            var currTime = new Date().getTime();
            var timeToCall = Math.max(0, 16 - (currTime - lastTime));
            var id = window.setTimeout(function() { callback(currTime + timeToCall); }, 
              timeToCall);
            lastTime = currTime + timeToCall;
            return id;
        };
 
    if (!window.cancelAnimationFrame)
        window.cancelAnimationFrame = function(id) {
            clearTimeout(id);
        };
}());

function supports_html5_storage() {
    try {
        return 'localStorage' in window && window['localStorage'] !== null;
    } catch (e) {
        return false;
    }
}

// on the recommendation of HTML5: Up and Running
function getCursorPosition(el, ev) {
    var x, y;
    if (ev.pageX != undefined && ev.pageY != undefined) {
        x = ev.pageX;
        y = ev.pageY;
    } else {
        x = ev.clientX + document.body.scrollLeft + document.documentElement.scrollLeft;
        y = ev.clientY + document.body.scrollTop + document.documentElement.scrollTop;
    }

    x -= el.offsetLeft;
    y -= el.offsetTop;

    return {x: x, y: y};
}

function saveArrows(arrows) {
    var i;

    if (!supports_html5_storage()) {
        return;
    }

    localStorage["test.arrowcount"] = arrows.length;

    for (i = 0; i < arrows.length; i++) {
        localStorage["test.arrow"+i] = arrows[i].start.x +"," + arrows[i].start.y + "," + arrows[i].end.x + "," + arrows[i].end.y;
    }
}

function loadArrows() {
    var arrows, arrowcount;

    if (!supports_html5_storage()) {
        return {};
    }

    arrowcount = parseInt(localStorage["test.arrowcount"]);
    if (isNaN(arrowcount)) {
        arrowcount = 0;
    }

    arrows = [];

    var parsePoints = function(s) {
        var a;
        if (typeof s != 'string') {
            return {};
        }
        
        a = s.split(',');
        if (a.length != 4) {
            return {};
        }

        return {start: {x: parseInt(a[0]),
                        y: parseInt(a[1])},
                  end: {x: parseInt(a[2]),
                        y: parseInt(a[3])}};
    };

    for (i = 0; i < arrowcount; i++) {
        arrows[i] = parsePoints(localStorage["test.arrow"+i]);
        window.console.log(localStorage["test.arrow"+i]);
        if (!('start' in arrows[i])) {
            return {};
        }
    }

    return arrows;
}

function initCanvas() {
    var arrow = {};
    var arrows ;

    if (!supports_canvas()) {
        return;
    }

    arrows = loadArrows();

    var canvasElement = document.createElement("canvas");
    canvasElement.id = "test_canvas";
    document.body.appendChild(canvasElement);
    canvasElement.width = kWidth;
    canvasElement.height= kHeight;

    context = canvasElement.getContext("2d");

    var animationStartTime = Date.now();
    var lastFrameTime = animationStartTime;
    var animateFcn = function (time) {
        var i;
        var dt = time-lastFrameTime;

        window.requestAnimationFrame(animateFcn);
        lastFrameTime = time;

        context.clearRect(0, 0, kWidth, kHeight);

        for (i = 0; i < arrows.length; i++) {
            context.beginPath();
            context.moveTo(arrows[i].start.x+0.5, arrows[i].start.y+0.5);
            context.lineTo(arrows[i].end.x+0.5, arrows[i].end.y+0.5);
            context.stroke();

            arrows[i].start.y += dt/1000*100;
            arrows[i].end.y += dt/1000*100;

            if (arrows[i].start.y > kHeight && arrows[i].end.y > kHeight) {
                var distdiff = Math.abs(arrows[i].end.y - arrows[i].start.y); 
                arrows[i].start.y -= kHeight + distdiff;
                arrows[i].end.y -= kHeight +   distdiff;
            }
        }

        if ('start' in arrow) {
            context.beginPath();
            context.moveTo(arrow.start.x+0.5, arrow.start.y+0.5);
            context.lineTo(arrow.end.x+0.5, arrow.end.y+0.5);
            context.stroke();
        }
    }

    initMouseEvents(canvasElement, {
        drag: function (p) {
            arrow.end = p;
        },
        pickup: function (p) {
            arrow = {start: p, end: p};
        },
        drop: function (p) {
            arrow.end = p;
            arrows[arrows.length] = arrow;
            arrow = {};

            saveArrows(arrows);
        }
    });


    window.requestAnimationFrame(animateFcn);
}

function initMouseEvents(canvasElement, fcnDict) {
    var in_range = false;
    var dragging = false;
    var last_point = {x:0, y:0};

    var handleMouseDrags = function (e, old_dragging) {
        var p = getCursorPosition(canvasElement, e);

        if (!old_dragging && dragging) {
            fcnDict.pickup(p);
            last_point = p;
        }
        if (old_dragging && !dragging) {
            fcnDict.drop(last_point);
        }
        if (old_dragging && dragging) {
            fcnDict.drag(p);
            last_point = p;
        }
    };

    canvasElement.addEventListener("mousedown", function (e) {
        var old_dragging = dragging;

        if (in_range) {
            dragging = true;
        }

        handleMouseDrags(e, old_dragging);
    }, false);
    canvasElement.addEventListener("mouseup", function (e) {
        var old_dragging = dragging;

        if (in_range) {
            dragging = false;
        }

        handleMouseDrags(e, old_dragging);
    }, false);
    canvasElement.addEventListener("mousemove", function (e) {
        if (in_range && dragging) {
            handleMouseDrags(e, dragging);
        }
    }, false);
    canvasElement.addEventListener("mouseover", function (e) {
        in_range = true;
    }, false);
    canvasElement.addEventListener("mouseout", function (e) {
        var old_dragging = dragging;

        in_range = false;
        dragging = false;
        handleMouseDrags(e, old_dragging);
    }, false);
}
