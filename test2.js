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

function getCursorPosition(el, ev) {
    // on the recommendation of HTML5: Up and Running
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

function drawDot(context, p) {
    context.fillRect(p.x, p.y, 3, 3);
}

function initCanvas() {
    var arrow = {};

    if (!supports_canvas()) {
        return;
    }

    var canvasElement = document.createElement("canvas");
    canvasElement.id = "test_canvas";
    document.body.appendChild(canvasElement);
    canvasElement.width = kWidth;
    canvasElement.height= kHeight;

    context = canvasElement.getContext("2d");

    var animationStartTime = Date.now();
    var animateFcn = function (time) {
        window.requestAnimationFrame(animateFcn);

        context.clearRect(0, 0, kWidth, kHeight);

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
            //commitCurrentArrow(arrow, context);
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

        if (in_range && e.buttons == 1) {
            dragging = true;
        }

        handleMouseDrags(e, old_dragging);
    }, false);
    canvasElement.addEventListener("mouseup", function (e) {
        var old_dragging = dragging;

        if (in_range && e.buttons == 1) {
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
