package com.jugfiller;

import java.awt.*;

import org.tbot.graphics.MouseTrail;
import org.tbot.internal.AbstractScript;
import org.tbot.internal.Manifest;
import org.tbot.internal.event.listeners.PaintListener;
import org.tbot.internal.handlers.LogHandler;
import org.tbot.methods.*;
import org.tbot.methods.tabs.Inventory;
import org.tbot.methods.walking.Path;
import org.tbot.methods.walking.Walking;
import org.tbot.methods.web.banks.WebBanks;
import org.tbot.util.Condition;
import org.tbot.wrappers.Area;
import org.tbot.wrappers.GameObject;
import org.tbot.wrappers.Tile;
import org.tbot.wrappers.Timer;


@Manifest(name = "Container Filler", authors = "mano, eugene", version = 2.0, description = "Fills Vials/Jugs/Buckets at V.East")
public class JugFiller extends AbstractScript implements PaintListener
{
    private int filledCount = 0;
    private int runEnergy = Random.nextInt(30, 60);
    private MouseTrail mt = new MouseTrail();
    private Timer t = new Timer();

    private BotState currentState;

    private static final Area FOUNTAIN_AREA = new Area(new Tile(3238, 3431, 0), new Tile(3242, 3434, 0));
    private static final int EMPTY_ID = 1935; //ID of empty container
    private static final int FILLED_ID = 1937; //ID of filled container

    private boolean firstRun = true;

    @Override
    public int loop()
    {
        if (!Game.isLoggedIn())
            return 500;

        //Determine initial state of bot
        if(firstRun && currentState == null)
            currentState = getInitialState();

        switch(currentState)
        {
            //Walk to bank
            case WALKING_TO_BANK:
                LogHandler.log("Current State: " + currentState.toString());
                checkRun();
                Bank.openBank(WebBanks.VARROCK_EAST_BANK);
                Time.sleepUntil(new Condition() {
                    public boolean check() {
                        return Bank.isOpen();
                    }
                }, Random.nextInt(1800, 2400));
                currentState = BotState.USING_BANK;
                break;

            //Use bank, then walk to fountain
            case USING_BANK:
                if(Bank.isOpen())
                {
                    LogHandler.log("Current State: " + currentState.toString());

                    //Deposit everything into bank
                    if(Bank.depositAll())
                    {
                        Time.sleepUntil(new Condition() {

                            public boolean check() {
                                return !Inventory.contains(FILLED_ID);
                            }
                        }, Random.nextInt(500, 800));
                    }

                    //Withdraw empty jugs
                    if(Bank.contains(EMPTY_ID) && Bank.getCount(EMPTY_ID) >= 28) // If the bank contains more than 28 empty jugs.
                    {
                        if (Bank.withdraw(EMPTY_ID, 28)) // Then withdraw 28 of them.
                        {
                            Time.sleepUntil(new Condition() {
                                public boolean check() {
                                    return Inventory.contains(1935);
                                }
                            }, Random.nextInt(500, 800));
                            currentState = BotState.WALKING_TO_FOUNTAIN;
                        }
                    }
                    else
                    {
                        LogHandler.log("No more empty containers. Logging out.");
                        Game.logout();
                    }

                }
                else
                    currentState = BotState.WALKING_TO_BANK;
                break;

            //Walking to fountain
            case WALKING_TO_FOUNTAIN:
                LogHandler.log("Current State: " + currentState.toString());
                checkRun();
                Path pathToFountain = Walking.findPath(FOUNTAIN_AREA.getCentralTile()); //Find a path to the tile in fountainArea that is closest to the player. We could randomly generate a tile to walk to to make it look less 'botlike', but we're going for efficiency here.
                if (pathToFountain != null && pathToFountain.traverse())
                {
                    Time.sleep(Random.nextInt(400, 900));

                    if(FOUNTAIN_AREA.contains(Players.getLocal().getLocation()) && Players.getLocal().getAnimation() == -1)
                        currentState = BotState.FILLING;
                }
                break;

            //Filling jugs
            case FILLING:
                if(FOUNTAIN_AREA.contains(Players.getLocal().getLocation())) // If the player is near the fountain.
                {
                    LogHandler.log("Current State: " + currentState.toString());
                    GameObject fountain = GameObjects.getNearest("Fountain"); // Get the nearest fountain (there is only 1.)
                    if (fountain.isOnScreen() && fountain != null) // If the fountain is on the screen and is not null.
                    {
                        if (Inventory.useItemOn(EMPTY_ID, fountain)) //Use a container on the fountain.
                        {
                            LogHandler.log(Inventory.getCount("Filling containers..."));
                            Time.sleepUntil(new Condition() {
                                public boolean check() {
                                    return Inventory.containsAll(FILLED_ID)
                                            && Players.getLocal().getAnimation() == -1
                                            && Inventory.getCount(FILLED_ID) == 28;
                                }
                            });
                            filledCount += 28;
                            LogHandler.log("Containers all filled.");
                        }
                    }
                    else  // If the fountain is not on screen.
                    {
                        Camera.tiltRandomly();
                        Camera.turnTo(fountain); // Turn the camera so that the fountain is visible.
                        Time.sleep(300, 420);
                    }

                    //If inventory is full, run back to bank and deposit/withdraw empty jugs
                    if(Inventory.getCount(FILLED_ID) >= 28)
                    {
                        currentState = BotState.WALKING_TO_BANK;
                    }
                }
                else
                    currentState = BotState.WALKING_TO_FOUNTAIN;
                break;
        }

        randomCameraRotate();

        return 500;
    }

    private BotState getInitialState()
    {
        firstRun = false;

        if(!Inventory.isFull() || Inventory.getEmptySlots() == 28)
            return BotState.WALKING_TO_BANK;
        else if(Bank.isOpen())
            return BotState.USING_BANK;
        else if(Inventory.isFull() && Inventory.getCount(EMPTY_ID) >= 28)
            return BotState.WALKING_TO_FOUNTAIN;
        else if(FOUNTAIN_AREA.contains(Players.getLocal().getLocation()))
            return BotState.FILLING;

        //If for whatever reason none of the above tests pass, just walk to bank and let the loop take over
        return BotState.WALKING_TO_BANK;
    }

    private void checkRun()
    {
        if (!Walking.isRunEnabled() && (Walking.getRunEnergy() >= runEnergy))
        {
            if (Walking.setRun(true))
            {
                Time.sleepUntil(new Condition() {
                    public boolean check() {
                        return Walking.isRunEnabled();
                    }
                }, Random.nextInt(800, 1200));
                runEnergy = Random.nextInt(30, 60);
            }
        }
    }

    private void randomCameraRotate()
    {
        if(Math.random() <= 0.05)
            Camera.tiltRandomly();
    }

    private enum BotState
    {
        WALKING_TO_BANK,
        USING_BANK,
        WALKING_TO_FOUNTAIN,
        FILLING;
    }

    public void onRepaint(Graphics graphics) {
        mt.draw(graphics);
        mt.setColor(Color.white);
        graphics.drawString("Time elapsed: " + t.getTimeRunningString(), 100, 115);
        graphics.drawString("Jugs filled: " + filledCount, 100, 130);
    }
}