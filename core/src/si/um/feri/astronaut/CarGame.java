package si.um.feri.astronaut;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.TimeUtils;

import java.util.Iterator;

public class CarGame extends ApplicationAdapter {
    private Texture gasolineImage;
    private Texture carImage;
    private Texture backgroundImage;
    private Texture coneImage;
    private Texture personImage;
    private Sound gameSound;
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Rectangle car;
    private Array<Rectangle> gasolines;    // special LibGDX Array
    private Array<Rectangle> persons;
    private Array<Rectangle> cones;
    private long lastGasolineTime;
    private long lastPersonTime;
    private long lastConeTime;
    private int gasolinesCaughtScore;
    private int carHealth;    // starts with 100

    public BitmapFont font;

    // all values are set experimental
    private static final int SPEED = 600;    // pixels per second
    private static final int SPEED_GASOLINE = 200; // pixels per second
    private static int SPEED_CONE = 100;    // pixels per second
    private static final long CREATE_GASOLINE_TIME = 1000000000;    // ns
    private static final long CREATE_CONE_TIME = 2000000000;    // ns
    private static final long CREATE_PERSON_TIME = 2111111111;    // ns

    @Override
    public void create() {
        font = new BitmapFont();
        font.getData().setScale(2);
        gasolinesCaughtScore = 0;
        carHealth = 100;

        // default way to load a texture
        carImage = new Texture(Gdx.files.internal("car.png"));
        gasolineImage = new Texture(Gdx.files.internal("gasoline.png"));
        coneImage = new Texture(Gdx.files.internal("cone3.png"));
        personImage = new Texture(Gdx.files.internal("miha.png"));
        backgroundImage = new Texture(Gdx.files.internal("road.jpg"));
        gameSound = Gdx.audio.newSound(Gdx.files.internal("pick.wav"));

        // create the camera and the SpriteBatch
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch = new SpriteBatch();

        // create a Rectangle to logically represents the rocket
        car = new Rectangle();
        car.x = Gdx.graphics.getWidth() / 2f - carImage.getWidth() / 2f;    // center the rocket horizontally
        car.y = 20;    // bottom left corner of the rocket is 20 pixels above the bottom screen edge
        car.width = carImage.getWidth();
        car.height = carImage.getHeight();

        gasolines = new Array<Rectangle>();
        persons = new Array<Rectangle>();
        cones = new Array<Rectangle>();
        // add first astronaut and asteroid
        spawnCone();
        spawnAsteroid();
    }

    /**
     * Runs every frame.
     */
    @Override
    public void render() {
        // clear screen
        Gdx.gl.glClearColor(0, 0, 0f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // process user input
        if (Gdx.input.isTouched()) commandTouched();    // mouse or touch screen
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) commandMoveLeft();
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) commandMoveRight();
        if (Gdx.input.isKeyPressed(Input.Keys.A)) commandMoveLeftCorner();
        if (Gdx.input.isKeyPressed(Input.Keys.S)) commandMoveRightCorner();
        if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) commandExitGame();

        // check if we need to create a new astronaut/asteroid
        if (TimeUtils.nanoTime() - lastGasolineTime > CREATE_GASOLINE_TIME) spawnCone();
        if (TimeUtils.nanoTime() - lastPersonTime > CREATE_PERSON_TIME) spawnPerson();
        if (TimeUtils.nanoTime() - lastConeTime > CREATE_CONE_TIME) spawnAsteroid();

        if (carHealth > 0) {    // is game end?
            // move and remove any that are beneath the bottom edge of
            // the screen or that hit the rocket
            for (Iterator<Rectangle> it = cones.iterator(); it.hasNext(); ) {
                Rectangle cone = it.next();
                cone.y -= SPEED_CONE * Gdx.graphics.getDeltaTime();
                if (cone.y + coneImage.getHeight() < 0) it.remove();
                if (cone.overlaps(car)) {
                    gameSound.play();
                    carHealth--;
                }
            }

            for (Iterator<Rectangle> it = persons.iterator(); it.hasNext(); ) {
                Rectangle person = it.next();
                person.y -= SPEED_CONE * Gdx.graphics.getDeltaTime();
                if (person.y + personImage.getHeight() < 0) it.remove();
                if (person.overlaps(car)) {
                    gameSound.play();
                    carHealth = -1;

                    batch.begin();
                    {
                        font.setColor(Color.RED);
                        font.draw(batch, "The END, YOU ARE GOING TO PRISON", Gdx.graphics.getHeight() / 2f, Gdx.graphics.getHeight() / 2f);
                    }
                    batch.end();
                }
            }

            for (Iterator<Rectangle> it = gasolines.iterator(); it.hasNext(); ) {
                Rectangle cone = it.next();
                cone.y -= SPEED_GASOLINE * Gdx.graphics.getDeltaTime();
                if (cone.y + gasolineImage.getHeight() < 0) it.remove();    // from screen
                if (cone.overlaps(car)) {
                    gameSound.play();
                    gasolinesCaughtScore++;
                    if (gasolinesCaughtScore % 10 == 0) SPEED_CONE += 66;    // speeds up
                    it.remove();    // smart Array enables remove from Array
                }
            }
        } else {    // health of rocket is 0 or less
            batch.begin();
            {
                font.setColor(Color.RED);
                font.draw(batch, "The END, U ARE DEAD", Gdx.graphics.getHeight() / 2f, Gdx.graphics.getHeight() / 2f);
            }
            batch.end();
        }

        // tell the camera to update its matrices.
        camera.update();

        // tell the SpriteBatch to render in the
        // coordinate system specified by the camera
        batch.setProjectionMatrix(camera.combined);

        // begin a new batch and draw the rocket, astronauts, asteroids
        batch.begin();
        {    // brackets added just for indent
            System.out.println(Gdx.graphics.getWidth());
            System.out.println(Gdx.graphics.getHeight());
            batch.draw(backgroundImage, 0, 0);
            batch.draw(carImage, car.x, car.y);
            for (Rectangle asteroid : cones) {
                batch.draw(coneImage, asteroid.x, asteroid.y);
            }
            for (Rectangle person : persons) {
                batch.draw(personImage, person.x, person.y);
            }
            for (Rectangle astronaut : gasolines) {
                batch.draw(gasolineImage, astronaut.x, astronaut.y);
            }
            font.setColor(Color.YELLOW);
            font.draw(batch, "" + gasolinesCaughtScore, Gdx.graphics.getWidth() - 50, Gdx.graphics.getHeight() - 20);
            font.setColor(Color.GREEN);
            font.draw(batch, "" + carHealth, 20, Gdx.graphics.getHeight() - 20);
        }
        batch.end();
    }

    /**
     * Release all the native resources.
     */
    @Override
    public void dispose() {
        gasolineImage.dispose();
        coneImage.dispose();
        backgroundImage.dispose();
        carImage.dispose();
        personImage.dispose();
        gameSound.dispose();
        batch.dispose();
        font.dispose();
    }

    private void spawnCone() {
        Rectangle cone = new Rectangle();
        cone.x = MathUtils.random(0, Gdx.graphics.getWidth() - gasolineImage.getWidth());
        cone.y = Gdx.graphics.getHeight();
        cone.width = gasolineImage.getWidth();
        cone.height = gasolineImage.getHeight();
        gasolines.add(cone);
        lastGasolineTime = TimeUtils.nanoTime();
    }

    private void spawnPerson() {
        Rectangle person = new Rectangle();
        person.x = MathUtils.random(0, Gdx.graphics.getWidth() - personImage.getWidth());
        person.y = Gdx.graphics.getHeight();
        person.width = personImage.getWidth();
        person.height = personImage.getHeight();
        persons.add(person);
        lastPersonTime = TimeUtils.nanoTime();
    }

    private void spawnAsteroid() {
        Rectangle asteroid = new Rectangle();
        asteroid.x = MathUtils.random(0, Gdx.graphics.getWidth() - gasolineImage.getWidth());
        asteroid.y = Gdx.graphics.getHeight();
        asteroid.width = coneImage.getWidth();
        asteroid.height = coneImage.getHeight();
        cones.add(asteroid);
        lastConeTime = TimeUtils.nanoTime();
    }

    private void commandMoveLeft() {
        car.x -= SPEED * Gdx.graphics.getDeltaTime();
        if (car.x < 0) car.x = 0;
    }

    private void commandMoveRight() {
        car.x += SPEED * Gdx.graphics.getDeltaTime();
        if (car.x > Gdx.graphics.getWidth() - carImage.getWidth())
            car.x = Gdx.graphics.getWidth() - carImage.getWidth();
    }

    private void commandMoveLeftCorner() {
        car.x = 0;
    }

    private void commandMoveRightCorner() {
        car.x = Gdx.graphics.getWidth() - carImage.getWidth();
    }

    private void commandTouched() {
        Vector3 touchPos = new Vector3();
        touchPos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(touchPos); // transform the touch/mouse coordinates to our camera's coordinate system
        car.x = touchPos.x - carImage.getWidth() / 2f;
    }

    private void commandExitGame() {
        Gdx.app.exit();
    }
}
