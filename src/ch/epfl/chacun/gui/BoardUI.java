package ch.epfl.chacun.gui;

import ch.epfl.chacun.*;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorInput;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;

import java.util.*;
import java.util.function.Consumer;

import static ch.epfl.chacun.Preconditions.checkArgument;
import static ch.epfl.chacun.gui.ColorMap.fillColor;
import static ch.epfl.chacun.gui.ImageLoader.*;

/**
 * Creates the graphical interface that displays the game board.
 *
 * @author Sam Lee (375535)
 */
public final class BoardUI {
    private BoardUI() {}

    // TODO
    // L'expression est recalculée plusieurs fois (Recalcul répété des instances de Blend / ColorInput,
    // au lieu de faire une instance par case et de mettre à jour ses propriétés

    /**
     * Creates the graphical interface that displays the game board.
     *
     * @param scope the scope of the board to be created (equal to 12 in this project)
     * @param gameStateO the game state
     * @param rotationO the rotation to apply to the tile to be placed
     * @param visibleOccupantsO all visible occupants
     * @param tileIdsO the set of identifiers of the highlighted tiles
     * @param rotateHandler a handler called when the current player wishes to rotate the tile to
     *                      be placed, i.e. he right-clicks on a box in the fringe
     * @param placeHandler a handler called when the current player wishes to place the tile to be
     *                     placed, i.e. he left-clicks on a box in the fringe
     * @param occupantHandler a handler called when the current player selects an occupant, i.e.
     *                        click on one of them
     * @return the graphical interface that displays the game board
     */
    public static Node create(int scope,
                              ObservableValue<GameState> gameStateO,
                              ObservableValue<Rotation> rotationO,
                              ObservableValue<Set<Occupant>> visibleOccupantsO,
                              ObservableValue<Set<Integer>> tileIdsO,
                              Consumer<Rotation> rotateHandler,
                              Consumer<Pos> placeHandler,
                              Consumer<Occupant> occupantHandler) {
        checkArgument(scope > 0);

        ObservableValue<Board> boardO = gameStateO.map(GameState::board);
        ObservableValue<PlayerColor> currentPlayerO = gameStateO.map(GameState::currentPlayer);
        ObservableValue<Tile> tileToPlaceO = gameStateO.map(GameState::tileToPlace);

        ObservableValue<Set<Pos>> fringeO = boardO.map(Board::insertionPositions);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setId("board-scroll-pane");
        scrollPane.getStylesheets().add("/board.css");
        scrollPane.setHvalue(0.5);
        scrollPane.setVvalue(0.5);

        GridPane gridPane = new GridPane();
        gridPane.setId("board-grid");
        scrollPane.setContent(gridPane);

//        StackPane stackPane = new StackPane();
//        ImageView animalScore = new ImageView("animalScore.png");
//        stackPane.getChildren().addAll(scrollPane, animalScore);
//        StackPane.setAlignment(animalScore, javafx.geometry.Pos.TOP_LEFT);

        for (int x = -scope; x <= scope; x++)
            for (int y = -scope; y <= scope; y++) {
                ObjectProperty<CellData> cellData = new SimpleObjectProperty<>();

                Group group = new Group();
                gridPane.add(group, x + scope, y + scope);

                ImageView imageView = new ImageView();
                imageView.setFitWidth(NORMAL_TILE_FIT_SIZE);
                imageView.setFitHeight(NORMAL_TILE_FIT_SIZE);
                imageView.imageProperty().bind(cellData.map(CellData::bgImage));
                group.getChildren().add(imageView);

                Pos pos = new Pos(x, y);
                ObservableValue<PlacedTile> tileO = boardO.map(b -> b.tileAt(pos));

                // only cells containing a tile have occupants and cancellation tokens
                tileO.addListener((_, oV, nV) -> {
                    if (oV == null) {
                        group.getChildren().addAll(markers(nV, boardO));
                        group.getChildren().addAll(occupants(nV, tileO, visibleOccupantsO, occupantHandler));
                    }
                });

                BooleanBinding onFringe = Bindings.createBooleanBinding(
                        () -> tileToPlaceO.getValue() != null && fringeO.getValue().contains(pos),
                        tileToPlaceO,
                        fringeO);

                cellData.bind(Bindings.createObjectBinding(() -> {
                            PlacedTile tile = tileO.getValue();

                            if (tile != null)
                                return tileIdsO.getValue().isEmpty() || tileIdsO.getValue().contains(tile.id())
                                        ? new CellData(tile, Color.TRANSPARENT)
                                        : new CellData(tile, Color.BLACK);

                            if (onFringe.getValue()
                                    && currentPlayerO.getValue() != null) {
                                PlacedTile tileToPlace = new PlacedTile(tileToPlaceO.getValue(),
                                        currentPlayerO.getValue(),
                                        rotationO.getValue(),
                                        pos);

                                if (group.isHover()) {
                                    Color color = boardO.getValue().canAddTile(tileToPlace)
                                            ? Color.TRANSPARENT
                                            : Color.WHITE;
                                    return new CellData(tileToPlace, color);
                                }
                                return new CellData(fillColor(currentPlayerO.getValue()));
                            }

                            return new CellData(Color.TRANSPARENT);
                        },
                        tileO,
                        tileIdsO,
                        group.hoverProperty(),
                        currentPlayerO,
                        rotationO,
                        tileToPlaceO,
                        onFringe));

                group.setOnMouseClicked(e -> {
                    if (fringeO.getValue().contains(pos)) {
                        switch (e.getButton()) {
                            case PRIMARY -> {
                                if (e.isStillSincePress()) placeHandler.accept(pos);
                            }
                            case SECONDARY -> {
                                if (e.isStillSincePress())
                                    rotateHandler.accept(e.isAltDown() ? Rotation.RIGHT : Rotation.LEFT);
                            }
                        }
                    }
                });

                group.rotateProperty().bind(cellData.map(CellData::rotation));

                group.effectProperty().bind(cellData.map(c -> {
                    ColorInput color =
                            new ColorInput(0, 0, NORMAL_TILE_FIT_SIZE, NORMAL_TILE_FIT_SIZE, c.veil());
                    Blend blend = new Blend(BlendMode.SRC_OVER);
                    blend.setOpacity(0.5);
                    blend.setTopInput(color);
                    blend.setBottomInput(null);

                    return blend;
                }));
            }

        return scrollPane;
    }

    private static List<ImageView> markers(PlacedTile tile,
                                           ObservableValue<Board> boardO) {
        return tile.meadowZones().stream()
                .flatMap(meadow -> meadow.animals().stream())
                .map(animal -> {
                    ImageView marker = new ImageView("marker.png");

                    marker.setFitWidth(MARKER_FIT_SIZE);
                    marker.setFitHeight(MARKER_FIT_SIZE);
                    marker.setId(STR."marker_\{animal.id()}");
                    marker.visibleProperty()
                            .bind(boardO.map(b -> b.cancelledAnimals().contains(animal)));

                    return marker;
                }).toList();
    }

    private static List<Node> occupants(PlacedTile tile,
                                        ObservableValue<PlacedTile> tileO,
                                        ObservableValue<Set<Occupant>> visibleOccupantsO,
                                        Consumer<Occupant> occupantHandler) {
        return tile.potentialOccupants().stream().map(occupant -> {
            Node icon = Icon.newFor(tile.placer(), occupant.kind());
            icon.setId(STR."\{occupant.kind().toString().toLowerCase()}_\{occupant.zoneId()}");

            icon.visibleProperty()
                    .bind(visibleOccupantsO.map(s -> s.contains(occupant)));
            icon.setOnMouseClicked(e -> {
                if (e.isStillSincePress()) occupantHandler.accept(occupant);
            });

            // It should always appear vertical when tile box is rotated
            icon.rotateProperty()
                    .bind(tileO.map(t -> t.rotation().negated().degreesCW()));

            return icon;
        }).toList();
    }

    private record CellData(Image bgImage, int rotation, Color veil) {
        private static final Map<Integer, Image> IMAGE_CACHE_BY_ID = new HashMap<>();
        private static final WritableImage EMPTY_IMAGE = new WritableImage(1,1);
        static{
            EMPTY_IMAGE.getPixelWriter().setColor( 0 , 0 , Color.gray(0.98));
        }

        private CellData(PlacedTile tile, Color veil) {
            this(IMAGE_CACHE_BY_ID.computeIfAbsent(tile.id(), ImageLoader::normalImageForTile),
                    tile.rotation().degreesCW(),
                    veil);
        }

        private CellData(Color veil) {
            this(EMPTY_IMAGE, 0, veil);
        }
    }
}