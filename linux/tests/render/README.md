# Rendering adapter checks

After building, run `PALADOG_CACHE=/path/to/cache tests/render/run-tests.sh` in a desktop session. In the cloud test environment:

```sh
PALADOG_CACHE=/workspace/paladog-linux-cache \
PALADOG_TEST_ENV=/workspace/paladog-linux-cache/test-env \
tools/test-display.sh exec tests/render/run-tests.sh
```

`BitmapProbe` checks exact premultiplied RGBA bytes, PNG encode/decode alpha preservation, retained metadata after bitmap recycling, Korean glyph coverage in the pinned Noto font, and antialiased ALPHA_8 text rasterization. `RenderProbe` creates a real GLFW/OpenGL context, uses the same GL10 bridge as the game, draws RGBA and ALPHA_8 textures, and reads the resulting pixels. It checks the two different cocos2d blending modes against numeric expectations and requires no OpenGL errors. It also verifies that direct texture deletion from a simulated finalizer is deferred until the context owner drains the queue.

`GLResourceThreadProbe` reproduces the original cleanup race: a finalizer queues a task during a drawing frame. It verifies the task does not run on the finalizer thread, is drained exactly once by the render thread, and that foreign threads cannot drain the queue. This prevents calls to desktop OpenGL without a current context.

These are adapter checks. They do not substitute for menu, battle, input, text layout and save/load checks in the running game. Software Mesa is sufficient for these checks; hardware driver coverage requires running on the intended Linux desktop.
