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

function circle(context, x, y, radius, color) {
        context.strokeStyle = color;
        context.fillStyle = color;
        context.beginPath();
        context.arc(x, y, radius, 0, Math.PI*2, false);
        context.closePath();
        context.fill();
};

function makeChannelString(v) {
        var i, s;
        
        i = Math.floor(Math.min(255, Math.max(0, v)));
        if (isNaN(i)) {
            i = 0;
        }

        s = i.toString(16);
        if (s.length == 1) {
            s = "0" + s;
        }
        return s;
};

function makeColor(r,g,b) {
        return '#' + makeChannelString(r) + makeChannelString(g) + makeChannelString(b);
};

// user code
function render(ctx, t) {

var wave_4_osc = 50 * Math.sin((t + 250) / 1000 * Math.PI*2);
var wave_6_osc = 50 * Math.sin(t / 1000 * Math.PI*2);
var circle_3_x = wave_4_osc + 128;
var circle_3_y = wave_6_osc + 128;
var circle_3_size = 10;
var circle_3_color = "red";
circle(ctx, circle_3_x,circle_3_y,circle_3_size,circle_3_color);
var color_7_red = 0;
var color_7_green = 0;
var color_7_blue = 255;
var color_7_rgb = makeColor(color_7_red, color_7_green, color_7_blue);
var wave_8_osc = 50 * Math.sin(t / 1500 * Math.PI*2);
var circle_5_x = wave_6_osc + 128;
var circle_5_y = wave_8_osc + 128;
var circle_5_size = 15;
var circle_5_color = color_7_rgb;
circle(ctx, circle_5_x,circle_5_y,circle_5_size,circle_5_color);
var wave_2_osc = 128 * Math.sin(t / 1000 * Math.PI*2) + 127;
var color_1_red = 0;
var color_1_green = wave_2_osc;
var color_1_blue = 0;
var color_1_rgb = makeColor(color_1_red, color_1_green, color_1_blue);
var circle_0_x = 0 + 128;
var circle_0_y = 0 + 128;
var circle_0_size = 30;
var circle_0_color = color_1_rgb;
circle(ctx, circle_0_x,circle_0_y,circle_0_size,circle_0_color);
}
