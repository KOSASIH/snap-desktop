/*
 * Copyright (C) 2011 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.snap.ui.product;

import com.bc.ceres.swing.figure.Figure;
import com.bc.ceres.swing.figure.support.DefaultFigureStyle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import junit.framework.TestCase;
import org.esa.snap.core.datamodel.SceneTransformProvider;
import org.esa.snap.core.transform.MathTransform2D;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import static org.esa.snap.core.datamodel.PlainFeatureFactory.createPlainFeature;
import static org.esa.snap.core.datamodel.PlainFeatureFactory.createPlainFeatureType;

public class SimpleFeatureShapeFigureTest {

    private final GeometryFactory gf = new GeometryFactory();
    private SceneTransformProvider sceneTransformProvider;

    @Before
    public void setUp() {
        sceneTransformProvider = new SceneTransformProvider() {
            @Override
            public MathTransform2D getModelToSceneTransform() {
                return MathTransform2D.IDENTITY;
            }

            @Override
            public MathTransform2D getSceneToModelTransform() {
                return MathTransform2D.IDENTITY;
            }
        };
    }

    @Test
    public void testSpecificGeometryType() {
        SimpleFeatureType sft = createPlainFeatureType("Polygon", Polygon.class, DefaultGeographicCRS.WGS84);

        Polygon polygon = createPolygon();
        SimpleFeature simpleFeature = createPlainFeature(sft, "_1", polygon, "");

        SimpleFeatureShapeFigure shapeFigure = new SimpleFeatureShapeFigure(simpleFeature, sceneTransformProvider, new DefaultFigureStyle());
        Assert.assertEquals(polygon, shapeFigure.getGeometry());
        Assert.assertNotNull(shapeFigure.getShape());
        Assert.assertEquals(Figure.Rank.AREA, shapeFigure.getRank());
    }

    @Test
    public void testMixedGeometries_2() {

        SimpleFeatureType sft = createPlainFeatureType("Geometry", Geometry.class, DefaultGeographicCRS.WGS84);

        Geometry geometry;
        SimpleFeature feature;
        SimpleFeatureShapeFigure figure;

        geometry = createPoint();
        feature = createPlainFeature(sft, "_4", geometry, "");
        figure = new SimpleFeatureShapeFigure(feature, sceneTransformProvider, new DefaultFigureStyle());
        Assert.assertEquals(geometry, figure.getGeometry());
        Assert.assertNotNull(figure.getShape());
        Assert.assertEquals(Figure.Rank.POINT, figure.getRank());

        geometry = createGeometryCollection();
        feature = createPlainFeature(sft, "_5", geometry, "");
        figure = new SimpleFeatureShapeFigure(feature, sceneTransformProvider, new DefaultFigureStyle());
        Assert.assertEquals(geometry, figure.getGeometry());
        Assert.assertNotNull(figure.getShape());
        Assert.assertEquals(Figure.Rank.NOT_SPECIFIED, figure.getRank());
    }

    @Test
    public void testMixedGeometries_1() {
        SimpleFeatureType sft = createPlainFeatureType("Geometry", Geometry.class, DefaultGeographicCRS.WGS84);

        Geometry geometry;
        SimpleFeature feature;
        SimpleFeatureShapeFigure figure;

        geometry = createPolygon();
        feature = createPlainFeature(sft, "_1", geometry, "");
        figure = new SimpleFeatureShapeFigure(feature, sceneTransformProvider, new DefaultFigureStyle());
        Assert.assertEquals(geometry, figure.getGeometry());
        Assert.assertNotNull(figure.getShape());
        Assert.assertEquals(Figure.Rank.AREA, figure.getRank());

        geometry = createLinearRing();
        feature = createPlainFeature(sft, "_2", geometry, "");
        figure = new SimpleFeatureShapeFigure(feature, sceneTransformProvider, new DefaultFigureStyle());
        Assert.assertEquals(geometry, figure.getGeometry());
        Assert.assertNotNull(figure.getShape());
        Assert.assertEquals(Figure.Rank.LINE, figure.getRank());

        geometry = createLineString();
        feature = createPlainFeature(sft, "_3", geometry, "");
        figure = new SimpleFeatureShapeFigure(feature, sceneTransformProvider, new DefaultFigureStyle());
        Assert.assertEquals(geometry, figure.getGeometry());
        Assert.assertNotNull(figure.getShape());
        Assert.assertEquals(Figure.Rank.LINE, figure.getRank());
    }

    @Test
    public void testRank() {
        Assert.assertEquals(Figure.Rank.POINT, SimpleFeatureShapeFigure.getRank(createPoint()));
        Assert.assertEquals(Figure.Rank.POINT, SimpleFeatureShapeFigure.getRank(createMultiPoint()));
        Assert.assertEquals(Figure.Rank.LINE, SimpleFeatureShapeFigure.getRank(createLineString()));
        Assert.assertEquals(Figure.Rank.LINE, SimpleFeatureShapeFigure.getRank(createLinearRing()));
        Assert.assertEquals(Figure.Rank.LINE, SimpleFeatureShapeFigure.getRank(createMultiLineString()));
        Assert.assertEquals(Figure.Rank.AREA, SimpleFeatureShapeFigure.getRank(createPolygon()));
        Assert.assertEquals(Figure.Rank.AREA, SimpleFeatureShapeFigure.getRank(createMultiPolygon()));
        Assert.assertEquals(Figure.Rank.NOT_SPECIFIED, SimpleFeatureShapeFigure.getRank(createGeometryCollection()));
    }

    private Point createPoint() {
        return gf.createPoint(new Coordinate(0, 0));
    }

    private LineString createLineString() {
        return gf.createLineString(new Coordinate[]{
                new Coordinate(0, 0),
                new Coordinate(1, 0),
                new Coordinate(1, 1),
                new Coordinate(0, 1),
        });
    }

    private LinearRing createLinearRing() {
        return gf.createLinearRing(new Coordinate[]{
                new Coordinate(0, 0),
                new Coordinate(1, 0),
                new Coordinate(1, 1),
                new Coordinate(0, 1),
                new Coordinate(0, 0),
        });
    }

    private Polygon createPolygon() {
        return gf.createPolygon(gf.createLinearRing(new Coordinate[]{
                new Coordinate(0, 0),
                new Coordinate(1, 0),
                new Coordinate(0, 1),
                new Coordinate(0, 0),
        }), null);
    }

    private MultiPoint createMultiPoint() {
        return gf.createMultiPoint(new Point[0]);
    }

    private MultiPolygon createMultiPolygon() {
        return gf.createMultiPolygon(new Polygon[0]);
    }

    private MultiLineString createMultiLineString() {
        return gf.createMultiLineString(new LineString[0]);
    }

    private GeometryCollection createGeometryCollection() {
        return gf.createGeometryCollection(new Geometry[0]);
    }
}
