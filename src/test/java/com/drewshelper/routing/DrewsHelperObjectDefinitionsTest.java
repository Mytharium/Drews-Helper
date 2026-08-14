package com.drewshelper.routing;

import java.lang.reflect.Proxy;
import net.runelite.api.ObjectComposition;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DrewsHelperObjectDefinitionsTest
{
    @Test
    public void activeDoesNotCallGetImpostorWhenNoImpostorIdsExist()
    {
        ObjectComposition base = composition(100, "Large door", new String[]{"Open", null, "Examine"}, null, null);

        assertNull(DrewsHelperObjectDefinitions.active(base));
        assertSame(base, DrewsHelperObjectDefinitions.activeOrBase(base));
        assertArrayEquals(new String[]{"Open", null, "Examine"},
            DrewsHelperObjectDefinitions.activeActions(base));
    }

    @Test
    public void activeActionsUseTheCurrentImpostorWhenPresent()
    {
        ObjectComposition openDoor = composition(101, "Large door", new String[]{"Close", null, "Examine"}, null, null);
        ObjectComposition base = composition(100, "Large door", new String[]{"Open", null, "Examine"}, new int[]{101}, openDoor);

        assertSame(openDoor, DrewsHelperObjectDefinitions.active(base));
        assertArrayEquals(new String[]{"Close", null, "Examine"},
            DrewsHelperObjectDefinitions.activeActions(base));
    }

    @Test
    public void actionMatchingAndTokensIgnoreTagsAndSpaces()
    {
        String[] actions = {"<col=00ff00>Open", "Climb-up", "Large door", null};

        assertTrue(DrewsHelperObjectDefinitions.hasAction(actions, "Open"));
        assertEquals("Open|Climb-up|Large_door", DrewsHelperObjectDefinitions.actionTokenList(actions));
    }

    static ObjectComposition composition(
        int id,
        String name,
        String[] actions,
        int[] impostorIds,
        ObjectComposition impostor
    )
    {
        return composition(id, name, actions, impostorIds, impostor, -1, -1, 1, 1);
    }

    static ObjectComposition composition(
        int id,
        String name,
        String[] actions,
        int[] impostorIds,
        ObjectComposition impostor,
        int varbit,
        int varp,
        int sizeX,
        int sizeY
    )
    {
        return (ObjectComposition) Proxy.newProxyInstance(
            ObjectComposition.class.getClassLoader(),
            new Class[]{ObjectComposition.class},
            (proxy, method, args) -> {
                switch (method.getName())
                {
                    case "getId":
                        return id;
                    case "getName":
                        return name;
                    case "getActions":
                        return actions;
                    case "getImpostorIds":
                        return impostorIds;
                    case "getImpostor":
                        if (impostorIds == null)
                        {
                            throw new IllegalStateException("getImpostor called without ids");
                        }
                        return impostor;
                    case "getVarbitId":
                        return varbit;
                    case "getVarPlayerId":
                        return varp;
                    case "getSizeX":
                        return sizeX;
                    case "getSizeY":
                        return sizeY;
                    case "toString":
                        return "ObjectComposition(" + id + ")";
                    default:
                        Class<?> returnType = method.getReturnType();
                        if (returnType == boolean.class)
                        {
                            return false;
                        }
                        if (returnType == int.class)
                        {
                            return 0;
                        }
                        if (returnType == long.class)
                        {
                            return 0L;
                        }
                        return null;
                }
            }
        );
    }
}
